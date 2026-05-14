package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.config.SliderProperties;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.sse.SseEventBus;
import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class PasswordLoginService {

    private static final String LOGIN_URL = "https://www.goofish.com/im";
    private static final String HOMEPAGE_URL = "https://www.goofish.com";
    private static final int VERIFICATION_WAIT_TIMEOUT_SECONDS = 300;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    @Lazy
    private XianyuSliderStealthService sliderStealthService;

    @Autowired
    @Lazy
    private SliderAutoVerifyService sliderAutoVerifyService;

    @Autowired(required = false)
    private SliderProperties sliderProperties;

    @Autowired
    @Lazy
    private SliderBrowserFingerprintService fingerprintService;

    @Autowired
    private SseEventBus sseEventBus;

    @Autowired
    private PasswordLoginStateInspector stateInspector;

    @Autowired
    private PasswordLoginPageHelper pageHelper;

    @Autowired
    private PasswordLoginCookieVerifier cookieVerifier;

    @Autowired
    private CaptchaDebugImageService captchaDebugImageService;

    private final Set<Long> activeLoginAccounts = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 尝试通过账密登录恢复 Cookie。
     * 打开有头浏览器自动填写账号密码并提交，遇到人脸/扫码验证时等待用户在浏览器中手动完成。
     * 返回新的 Cookie 文本，失败返回 null。
     */
    public String tryPasswordLogin(Long accountId) {
        if (accountId == null) {
            return null;
        }
        if (!activeLoginAccounts.add(accountId)) {
            log.warn("[PasswordLogin] 账号{}已有账密登录流程在处理中，直接提示人工验证", accountId);
            notifyManualVerification(null, accountId, "登录人工验证", "账号密码登录正在处理中，请在弹窗中查看最新截图并完成人脸/扫码/二维码验证");
            return null;
        }
        try {
            return doTryPasswordLogin(accountId);
        } finally {
            activeLoginAccounts.remove(accountId);
        }
    }

    private String doTryPasswordLogin(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            log.warn("[PasswordLogin] 账号不存在: {}", accountId);
            return null;
        }
        String username = account.getLoginUsername();
        String password = account.getLoginPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("[PasswordLogin] 账号{}未配置登录凭据，跳过账密登录", accountId);
            notifyManualVerification(null, accountId, "账号密码未配置", "请先配置登录用户名和密码，或通过扫码/Cookie方式人工处理");
            return null;
        }

        log.info("[PasswordLogin] 开始账密登录恢复: accountId={}", accountId);
        try (Playwright playwright = Playwright.create()) {
            SliderBrowserFingerprintService.BrowserProfile profile = fingerprintService.profile(accountId);
            Path profileDir = Path.of(System.getProperty("user.dir"), "browser_data", "user_" + accountId);
            Files.createDirectories(profileDir);

            BrowserType.LaunchPersistentContextOptions options = buildOptions(profile);
            boolean headless = isHeadlessMode();
            BrowserContext context = playwright.chromium().launchPersistentContext(profileDir, options);
            // 有头模式下只注入轻量反检测脚本，完整脚本会覆盖浏览器核心 API 导致页面白屏
            if (headless) {
                String stealthScript = fingerprintService.stealthScript(profile);
                if (!stealthScript.isBlank()) {
                    context.addInitScript(stealthScript);
                }
            } else {
                context.addInitScript("""
                        Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                        Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
                        Object.defineProperty(navigator, 'languages', { get: () => ['zh-CN', 'zh', 'en'] });
                        window.chrome = { runtime: {} };
                        """);
            }

            try {
                Page page = context.newPage();
                fingerprintService.applyNetworkFingerprint(context, page, profile);

                // 预热：先访问首页
                page.navigate(HOMEPAGE_URL, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(15000));
                page.waitForTimeout(randomBetween(1000, 2000));

                // 导航到登录页
                page.navigate(LOGIN_URL, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
                page.waitForTimeout(randomBetween(2000, 3500));

                // 查找登录表单所在的 frame（闲鱼登录通常在 iframe 中）
                Frame loginFrame = pageHelper.findLoginFrame(page);
                if (loginFrame == null) {
                    log.warn("[PasswordLogin] 未找到登录frame，尝试在主页面查找");
                    loginFrame = page.mainFrame();
                }

                // 切换到密码登录标签
                pageHelper.clickPasswordLoginTab(loginFrame);
                page.waitForTimeout(randomBetween(800, 1500));

                // 填写账号
                ElementHandle accountInput = pageHelper.findAccountInput(loginFrame);
                if (accountInput == null) {
                    log.warn("[PasswordLogin] 未找到账号输入框，当前URL: {}, frames: {}",
                            page.url(), page.frames().size());
                    String recoveredCookie = recoverFromMissingInputs(page, context, accountId, "账号输入框");
                    if (recoveredCookie != null) {
                        return recoveredCookie;
                    }
                    return null;
                }
                accountInput.click();
                page.waitForTimeout(randomBetween(200, 500));
                accountInput.fill(username);
                page.waitForTimeout(randomBetween(300, 600));

                // 填写密码
                ElementHandle passwordInput = pageHelper.findPasswordInput(loginFrame);
                if (passwordInput == null) {
                    log.warn("[PasswordLogin] 未找到密码输入框");
                    String recoveredCookie = recoverFromMissingInputs(page, context, accountId, "密码输入框");
                    if (recoveredCookie != null) {
                        return recoveredCookie;
                    }
                    return null;
                }
                passwordInput.click();
                page.waitForTimeout(randomBetween(200, 500));
                passwordInput.fill(password);
                page.waitForTimeout(randomBetween(500, 1000));

                // 勾选协议
                try {
                    ElementHandle checkbox = loginFrame.querySelector("#fm-agreement-checkbox, input[type=\"checkbox\"]");
                    if (checkbox != null && checkbox.isVisible()) {
                        if (!checkbox.isChecked()) {
                            checkbox.click();
                            page.waitForTimeout(randomBetween(300, 600));
                        }
                    }
                } catch (Exception ignored) {}

                // 点击登录按钮
                log.info("[PasswordLogin] 点击登录按钮");
                if (!pageHelper.clickSubmit(loginFrame)) {
                    ElementHandle passwordInput2 = pageHelper.findPasswordInput(loginFrame);
                    if (passwordInput2 != null) passwordInput2.press("Enter");
                }
                page.waitForTimeout(randomBetween(3000, 5000));

                // 登录后检测滑块验证（滑块在点击登录后出现）
                // 外层最多重试 3 次：失败重新点击登录触发新滑块；硬拒绝（"验证失败，点击框体重试"等）立刻走人工兜底
                handleSliderWithRetry(page, loginFrame, accountId, 3);

                // 轮询等待登录成功（支持人脸/扫码/短信等验证方式）
                String cookieText = waitForLoginSuccess(page, context, accountId);
                if (cookieText != null) {
                    log.info("[PasswordLogin] 账密登录成功: accountId={}, cookieLength={}", accountId, cookieText.length());
                    notifyVerificationSuccess(accountId);
                    return cookieText;
                }

                log.warn("[PasswordLogin] 登录等待超时，当前URL: {}", page.url());
                notifyManualVerification(page, accountId, "登录人工验证", "账密登录未完成，请在截图页面中完成扫码、人脸或短信验证");
                return null;
            } finally {
                context.close();
            }
        } catch (Exception e) {
            log.error("[PasswordLogin] 账密登录异常: accountId={}", accountId, e);
            notifyManualVerification(null, accountId, "登录人工验证", "账号密码登录异常，请在弹窗中查看最新截图并完成人脸/扫码/二维码验证");
            return null;
        }
    }

    private String waitForLoginSuccess(Page page, BrowserContext context, Long accountId) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = VERIFICATION_WAIT_TIMEOUT_SECONDS * 1000L;
        boolean notified = false;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            String pageText = pageHelper.snapshotText(page);
            if (!notified && stateInspector.hasHardReject(pageText)) {
                log.warn("[PasswordLogin] 登录等待中命中硬拒绝，转人工处理: accountId={}", accountId);
                notifyManualVerification(page, accountId, "滑块硬拒绝", "验证失败，点击框体重试");
                notified = true;
            }

            PasswordLoginCookieVerifier.LoginCookieCheck cookieCheck = cookieVerifier.check(accountId, page, context);
            if (cookieCheck.success()) {
                return cookieCheck.cookieText();
            }

            // 检测是否需要人工验证（人脸/扫码/短信），只通知一次
            if (!notified && needsManualVerification(page)) {
                String verificationType = detectVerificationType(page);
                log.info("[PasswordLogin] 检测到需要人工验证: accountId={}, type={}", accountId, verificationType);
                notifyVerificationRequired(page, accountId, verificationType);
                notified = true;
            }

            page.waitForTimeout(3000);
        }
        return null;
    }

    private String recoverFromMissingInputs(Page page, BrowserContext context,
                                            Long accountId, String missingField) {
        PasswordLoginCookieVerifier.LoginCookieCheck cookieCheck = cookieVerifier.check(accountId, page, context);
        if (cookieCheck.success()) {
            log.info("[PasswordLogin] 未找到{}，但复检确认已登录: accountId={}", missingField, accountId);
            return cookieCheck.cookieText();
        }
        String text = pageHelper.snapshotText(page);
        if (stateInspector.hasHardReject(text)) {
            log.warn("[PasswordLogin] 未找到{}，页面已命中硬拒绝: accountId={}", missingField, accountId);
            notifyManualVerification(page, accountId, "滑块硬拒绝", "验证失败，点击框体重试");
            return waitForLoginSuccess(page, context, accountId);
        }
        if (stateInspector.hasManualVerification(text, page.url())) {
            String verificationType = detectVerificationType(page);
            log.info("[PasswordLogin] 未找到{}，页面需要人工验证: accountId={}, type={}",
                    missingField, accountId, verificationType);
            notifyVerificationRequired(page, accountId, verificationType);
            return waitForLoginSuccess(page, context, accountId);
        }
        if (!stateInspector.hasLoginFormHint(text)) {
            log.warn("[PasswordLogin] 未找到{}且页面无登录表单提示: accountId={}, url={}",
                    missingField, accountId, page.url());
            notifyManualVerification(page, accountId, "登录人工验证", "未找到" + missingField + "，请按截图页面提示处理");
        }
        return null;
    }

    private boolean needsManualVerification(Page page) {
        try {
            String text = pageHelper.snapshotText(page);
            if (stateInspector.hasManualVerification(text, page.url())) {
                return true;
            }
            // 人脸验证
            if (page.locator("[class*='face'], [class*='Face'], img[src*='face']").count() > 0) {
                return true;
            }
            // 扫码验证
            if (page.locator("[class*='qrcode'], [class*='QRCode'], img[src*='qrcode']").count() > 0) {
                return true;
            }
            // 短信验证
            if (page.locator("[class*='sms'], input[placeholder*='验证码'], input[placeholder*='短信']").count() > 0) {
                return true;
            }
            // 仍在登录/验证页面
            String url = page.url();
            if (url.contains("passport") || url.contains("verify") || url.contains("security")) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String detectVerificationType(Page page) {
        try {
            String text = pageHelper.snapshotText(page);
            if (stateInspector.hasHardReject(text)) return "滑块硬拒绝";
            if (text.contains("扫码") || text.contains("二维码")) return "扫码验证";
            if (text.contains("人脸") || text.contains("刷脸")) return "人脸验证";
            if (text.contains("短信") || text.contains("验证码")) return "短信验证";
            if (page.locator("[class*='face'], [class*='Face']").count() > 0) return "人脸验证";
            if (page.locator("[class*='qrcode'], [class*='QRCode']").count() > 0) return "扫码验证";
            if (page.locator("[class*='sms'], input[placeholder*='验证码']").count() > 0) return "短信验证";
        } catch (Exception ignored) {}
        return "身份验证";
    }

    private void notifyVerificationRequired(Page page, Long accountId, String verificationType) {
        Map<String, Object> payload = verificationPayload(
                page,
                accountId,
                verificationType,
                "",
                "账号" + accountId + "登录需要" + verificationType + "，请在前端弹窗中完成人工处理"
        );
        sseEventBus.broadcast("notification", payload);
    }

    /**
     * 滑块外层重试：最多 attempts 次，失败时重新点击登录按钮触发新滑块。
     * 硬拒绝（"验证失败，点击框体重试" 等高风险拦截）立刻退出，由前端展示人脸/扫码界面让用户人工处理。
     */
    private void handleSliderWithRetry(Page page, Frame loginFrame, Long accountId, int attempts) {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (!pageHelper.hasSliderInAnyFrame(page)) {
                if (attempt == 1) {
                    log.info("[PasswordLogin] 登录后未检测到滑块，跳过滑块处理");
                }
                return;
            }
            log.info("[PasswordLogin] 第{}/{}次检测到滑块，调用自动滑块处理: accountId={}",
                    attempt, attempts, accountId);
            SliderAutoVerifyResult result = sliderAutoVerifyService.solveDetailed(page, accountId);
            if (result.isSuccess()) {
                log.info("[PasswordLogin] 第{}次滑块自动通过: accountId={}", attempt, accountId);
                page.waitForTimeout(randomBetween(2000, 3000));
                return;
            }
            if (result.isHardBlock()) {
                String reason = result.getBlock() != null ? result.getBlock().getMessage() : result.getMessage();
                log.warn("[PasswordLogin] 第{}次滑块命中硬拦截({})，停止重试，转人工处理: accountId={}",
                        attempt, reason, accountId);
                notifyManualVerification(page, accountId, "滑块硬拒绝（需要人脸/扫码登录）", reason);
                return;
            }
            if (attempt >= attempts) {
                log.warn("[PasswordLogin] 滑块累计{}次失败，转人工处理: accountId={}", attempt, accountId);
                notifyManualVerification(page, accountId, "滑块自动验证失败（请人脸/扫码登录）", result.getMessage());
                return;
            }
            log.info("[PasswordLogin] 第{}次滑块失败，重新点击登录按钮触发新滑块", attempt);
            page.waitForTimeout(randomBetween(1000, 1800));
            if (!pageHelper.clickSubmit(loginFrame)) {
                ElementHandle passwordInput = pageHelper.findPasswordInput(loginFrame);
                if (passwordInput != null) passwordInput.press("Enter");
            }
            page.waitForTimeout(randomBetween(3000, 5000));
        }
    }

    /**
     * 滑块无法自动通过时，通知前端展示人脸/扫码界面供用户人工处理。
     * 同时浏览器 context 保留打开，用户可直接在浏览器扫码或完成其他验证。
     */
    private void notifyManualVerification(Page page, Long accountId, String verificationType, String detail) {
        Map<String, Object> payload = verificationPayload(
                page,
                accountId,
                verificationType,
                detail,
                "账号" + accountId + verificationType + "，请通过前端弹窗中的人脸/扫码/二维码页面完成登录"
        );
        sseEventBus.broadcast("notification", payload);
    }

    private Map<String, Object> verificationPayload(Page page,
                                                    Long accountId,
                                                    String verificationType,
                                                    String detail,
                                                    String message) {
        captchaDebugImageService.capture(page, accountId, "password_login_manual");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("type", "verification_required");
        payload.put("verificationType", verificationType);
        payload.put("detail", detail == null ? "" : detail);
        payload.put("message", message);
        payload.put("screenshotUrl", captchaDebugImageService.latestImageUrl(accountId));
        payload.put("captchaImageUrl", captchaDebugImageService.latestImageUrl(accountId));
        return payload;
    }

    private void notifyVerificationSuccess(Long accountId) {
        Map<String, Object> payload = Map.of(
                "accountId", accountId,
                "type", "verification_success",
                "message", "账号" + accountId + "登录验证成功"
        );
        sseEventBus.broadcast("notification", payload);
    }

    private boolean isHeadlessMode() {
        boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        return isLinux && (System.getenv("DISPLAY") == null || System.getenv("DISPLAY").isBlank());
    }

    private BrowserType.LaunchPersistentContextOptions buildOptions(SliderBrowserFingerprintService.BrowserProfile profile) {
        boolean headless = isHeadlessMode();
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(headless)
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(List.of(
                        "--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage",
                        "--no-first-run", "--disable-blink-features=AutomationControlled",
                        "--window-size=" + profile.getViewportWidth() + "," + profile.getViewportHeight(),
                        "--lang=" + profile.getLocale(), "--mute-audio",
                        "--force-color-profile=srgb", "--password-store=basic", "--use-mock-keychain",
                        // ↓ adryfish/fingerprint-chromium 底层指纹开关，与 XianyuSliderStealthService 共用同一账号 seed
                        "--fingerprint=" + profile.getFingerprintSeed(),
                        "--fingerprint-platform=windows",
                        "--fingerprint-platform-version=10.0.0",
                        "--fingerprint-brand=Chrome",
                        "--fingerprint-brand-version=" + profile.getMajorVersion(),
                        "--fingerprint-hardware-concurrency=" + profile.getHardwareConcurrency(),
                        "--timezone=" + profile.getTimezoneId()
                ))
                .setViewportSize(profile.getViewportWidth(), profile.getViewportHeight())
                .setUserAgent(profile.getUserAgent())
                .setLocale(profile.getLocale())
                .setTimezoneId(profile.getTimezoneId())
                .setIgnoreHTTPSErrors(true);
        PlaywrightBrowserUtils.resolveChromiumPath().ifPresent(options::setExecutablePath);
        return options;
    }

    private long randomBetween(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }
}
