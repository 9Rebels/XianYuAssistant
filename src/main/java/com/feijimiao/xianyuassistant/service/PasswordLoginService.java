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
    private final Map<Long, PendingManualVerification> pendingManualVerifications =
            new java.util.concurrent.ConcurrentHashMap<>();

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

    public ManualVerificationConfirmResult confirmManualVerification(Long accountId) {
        if (accountId == null) {
            return ManualVerificationConfirmResult.failed("账号ID不能为空");
        }
        PendingManualVerification pending = pendingManualVerifications.get(accountId);
        if (pending == null) {
            return ManualVerificationConfirmResult.failed("没有正在等待的人工验证会话，请重新触发账号密码登录恢复");
        }
        synchronized (pending) {
            if (pending.isExpired()) {
                pendingManualVerifications.remove(accountId, pending);
                return ManualVerificationConfirmResult.failed("人工验证等待已超时，请重新触发账号密码登录恢复");
            }
            if (pending.isClosed()) {
                pendingManualVerifications.remove(accountId, pending);
                return ManualVerificationConfirmResult.failed("人工验证浏览器会话已关闭，请重新触发账号密码登录恢复");
            }
            PasswordLoginCookieVerifier.LoginCookieCheck cookieCheck =
                    cookieVerifier.check(accountId, pending.page, pending.context);
            if (!cookieCheck.success()) {
                captchaDebugImageService.capture(pending.page, accountId, "password_login_manual_confirm_failed");
                return ManualVerificationConfirmResult.failed(
                        "还没有检测到有效登录 Cookie：" + cookieCheck.message());
            }
            pending.confirmedCookieText = cookieCheck.cookieText();
            pending.confirmedAt = System.currentTimeMillis();
            return ManualVerificationConfirmResult.success(cookieCheck.cookieText());
        }
    }

    public List<ManualVerificationState> pendingManualVerifications() {
        long now = System.currentTimeMillis();
        return pendingManualVerifications.entrySet().stream()
                .map(entry -> toManualVerificationState(entry.getKey(), entry.getValue(), now))
                .filter(Objects::nonNull)
                .toList();
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
        SliderBrowserFingerprintService.BrowserProfile profile = fingerprintService.profile(accountId);
        Path profileDir = Path.of(System.getProperty("user.dir"), "browser_data", "user_" + accountId);
        ExternalBrowserLauncher launcher = null;
        try {
            Files.createDirectories(profileDir);
            List<String> launchArgs = buildExternalLaunchArgs(profile);
            launcher = ExternalBrowserLauncher.launch(launchArgs, profileDir, accountId);
        } catch (Exception e) {
            log.error("[PasswordLogin] 外部浏览器启动失败，回退 launchPersistentContext: accountId={}, error={}",
                    accountId, e.getMessage());
            if (launcher != null) launcher.shutdown();
            return doTryPasswordLoginFallback(accountId, username, password, profile, profileDir);
        }
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().connectOverCDP(launcher.getCdpUrl());
            BrowserContext context = browser.contexts().isEmpty()
                    ? browser.newContext() : browser.contexts().get(0);
            context.addInitScript(headfulStealthScript());

            try {
                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                return executeLoginFlow(page, context, accountId, username, password);
            } finally {
                clearPendingManualVerification(accountId, context);
                context.close();
            }
        } catch (Exception e) {
            log.error("[PasswordLogin] 账密登录异常: accountId={}", accountId, e);
            notifyManualVerification(null, accountId, "登录人工验证", "账号密码登录异常，请在弹窗中查看最新截图并完成人脸/扫码/二维码验证");
            return null;
        } finally {
            if (launcher != null) launcher.shutdown();
        }
    }

    private String waitForLoginSuccess(Page page, BrowserContext context, Long accountId) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = VERIFICATION_WAIT_TIMEOUT_SECONDS * 1000L;
        boolean notified = false;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            String confirmedCookieText = confirmedCookieText(accountId);
            if (confirmedCookieText != null) {
                return confirmedCookieText;
            }
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
        if (!stateInspector.hasLoginFormHint(text) && stateInspector.hasManualVerification(text, page.url())) {
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
            // 页面仍是登录表单（含密码登录/忘记密码等），不是验证挑战
            if (stateInspector.hasLoginFormHint(text)) {
                return false;
            }
            if (stateInspector.hasManualVerification(text, page.url())) {
                return true;
            }
            // 人脸验证
            if (page.locator("[class*='face'], [class*='Face'], img[src*='face']").count() > 0) {
                return true;
            }
            // 验证专用页面（排除 passport 登录页本身）
            String url = page.url();
            if (url.contains("verify") || url.contains("security")) {
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
        long expiresAt = registerPendingManualVerification(page, accountId);
        Map<String, Object> payload = verificationPayload(
                page,
                accountId,
                verificationType,
                "",
                "账号" + accountId + "登录需要" + verificationType + "，请在前端弹窗中完成人工处理",
                expiresAt
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
            // 滑块失败后检查页面是否已跳转到人脸/扫码验证
            if (needsManualVerification(page)) {
                String verificationType = detectVerificationType(page);
                log.info("[PasswordLogin] 第{}次滑块失败后页面已跳转到人工验证: accountId={}, type={}",
                        attempt, accountId, verificationType);
                notifyManualVerification(page, accountId, verificationType, "滑块验证失败后触发" + verificationType);
                return;
            }
            // no_elements 说明滑块消失了（页面可能已跳转），直接转人工
            if ("no_elements".equals(result.getMessage())) {
                log.warn("[PasswordLogin] 第{}次滑块失败且滑块元素消失，转人工处理: accountId={}", attempt, accountId);
                notifyManualVerification(page, accountId, "滑块验证失败（请人脸/扫码登录）", "滑块元素消失，页面可能已跳转到其他验证方式");
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
        long expiresAt = registerPendingManualVerification(page, accountId);
        Map<String, Object> payload = verificationPayload(
                page,
                accountId,
                verificationType,
                detail,
                "账号" + accountId + verificationType + "，请通过前端弹窗中的人脸/扫码/二维码页面完成登录",
                expiresAt
        );
        sseEventBus.broadcast("notification", payload);
    }

    private Map<String, Object> verificationPayload(Page page,
                                                    Long accountId,
                                                    String verificationType,
                                                    String detail,
                                                    String message,
                                                    long expiresAt) {
        captchaDebugImageService.capture(page, accountId, "password_login_manual");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("type", "verification_required");
        payload.put("verificationType", verificationType);
        payload.put("detail", detail == null ? "" : detail);
        payload.put("message", message);
        payload.put("screenshotUrl", captchaDebugImageService.latestImageUrl(accountId));
        payload.put("captchaImageUrl", captchaDebugImageService.latestImageUrl(accountId));
        payload.put("expiresAt", expiresAt);
        payload.put("timeoutSeconds", VERIFICATION_WAIT_TIMEOUT_SECONDS);
        updatePendingManualVerification(accountId, verificationType, detail, message, expiresAt);
        return payload;
    }

    private long registerPendingManualVerification(Page page, Long accountId) {
        long expiresAt = System.currentTimeMillis() + VERIFICATION_WAIT_TIMEOUT_SECONDS * 1000L;
        if (accountId == null || page == null || page.isClosed()) {
            return expiresAt;
        }
        try {
            BrowserContext context = page.context();
            pendingManualVerifications.put(accountId,
                    new PendingManualVerification(accountId, page, context, expiresAt));
        } catch (Exception e) {
            log.debug("[PasswordLogin] 记录人工验证浏览器会话失败: accountId={}, error={}",
                    accountId, e.getMessage());
        }
        return expiresAt;
    }

    private ManualVerificationState toManualVerificationState(Long accountId,
                                                              PendingManualVerification pending,
                                                              long now) {
        if (pending == null || pending.expiresAt <= now || pending.isClosed()) {
            pendingManualVerifications.remove(accountId, pending);
            return null;
        }
        return new ManualVerificationState(
                accountId,
                pending.verificationType,
                pending.message,
                pending.detail,
                captchaDebugImageService.latestImageUrl(accountId),
                pending.expiresAt,
                VERIFICATION_WAIT_TIMEOUT_SECONDS
        );
    }

    private void updatePendingManualVerification(Long accountId,
                                                 String verificationType,
                                                 String detail,
                                                 String message,
                                                 long expiresAt) {
        PendingManualVerification pending = pendingManualVerifications.get(accountId);
        if (pending == null) {
            return;
        }
        pending.verificationType = verificationType == null ? "人工验证" : verificationType;
        pending.detail = detail == null ? "" : detail;
        pending.message = message == null ? "账号登录需要人工处理" : message;
        pending.expiresAt = expiresAt;
    }

    private String confirmedCookieText(Long accountId) {
        PendingManualVerification pending = pendingManualVerifications.get(accountId);
        if (pending == null || pending.confirmedCookieText == null || pending.confirmedCookieText.isBlank()) {
            return null;
        }
        return pending.confirmedCookieText;
    }

    private void clearPendingManualVerification(Long accountId, BrowserContext context) {
        PendingManualVerification pending = pendingManualVerifications.get(accountId);
        if (pending != null && pending.context == context) {
            pendingManualVerifications.remove(accountId, pending);
        }
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
                        "--fingerprint=" + profile.getFingerprintSeed(),
                        "--fingerprint-platform=windows",
                        "--fingerprint-platform-version=10.0.0",
                        "--fingerprint-brand=Chrome",
                        "--fingerprint-brand-version=" + profile.getMajorVersion(),
                        "--fingerprint-hardware-concurrency=" + profile.getHardwareConcurrency(),
                        "--timezone=" + profile.getTimezoneId(),
                        "--use-gl=angle",
                        "--use-angle=swiftshader-webgl",
                        "--enable-unsafe-swiftshader"
                ))
                .setViewportSize(profile.getViewportWidth(), profile.getViewportHeight())
                .setUserAgent(profile.getUserAgent())
                .setLocale(profile.getLocale())
                .setTimezoneId(profile.getTimezoneId())
                .setIgnoreHTTPSErrors(true);
        PlaywrightBrowserUtils.resolveChromiumPath().ifPresent(options::setExecutablePath);
        return options;
    }

    private List<String> buildExternalLaunchArgs(SliderBrowserFingerprintService.BrowserProfile profile) {
        List<String> args = new ArrayList<>(fingerprintService.buildLaunchArgs(profile));
        args.add("--disable-background-networking");
        args.add("--disable-default-apps");
        args.add("--disable-extensions");
        args.add("--disable-sync");
        args.add("--disable-translate");
        args.add("--metrics-recording-only");
        args.add("--no-default-browser-check");
        args.add("--remote-allow-origins=*");
        return args;
    }

    private String doTryPasswordLoginFallback(Long accountId, String username, String password,
                                              SliderBrowserFingerprintService.BrowserProfile profile,
                                              Path profileDir) {
        log.info("[PasswordLogin] 使用 launchPersistentContext 回退模式: accountId={}", accountId);
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = buildOptions(profile);
            BrowserContext context = playwright.chromium().launchPersistentContext(profileDir, options);
            boolean headless = isHeadlessMode();
            if (headless) {
                String stealthScript = fingerprintService.stealthScript(profile);
                if (!stealthScript.isBlank()) {
                    context.addInitScript(stealthScript);
                }
            } else {
                context.addInitScript(headfulStealthScript());
            }
            try {
                Page page = context.newPage();
                fingerprintService.applyNetworkFingerprint(context, page, profile);
                return executeLoginFlow(page, context, accountId, username, password);
            } finally {
                clearPendingManualVerification(accountId, context);
                context.close();
            }
        } catch (Exception e) {
            log.error("[PasswordLogin] 回退模式也失败: accountId={}", accountId, e);
            notifyManualVerification(null, accountId, "登录人工验证", "账号密码登录异常");
            return null;
        }
    }

    private String executeLoginFlow(Page page, BrowserContext context, Long accountId,
                                    String username, String password) {
        page.navigate(HOMEPAGE_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(15000));
        page.waitForTimeout(randomBetween(1000, 2000));

        page.navigate(LOGIN_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
        page.waitForTimeout(randomBetween(2000, 3500));

        Frame loginFrame = pageHelper.findLoginFrame(page);
        if (loginFrame == null) {
            log.warn("[PasswordLogin] 未找到登录frame，尝试在主页面查找");
            loginFrame = page.mainFrame();
        }

        pageHelper.clickPasswordLoginTab(loginFrame);
        page.waitForTimeout(randomBetween(800, 1500));

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
        humanType(page, username);
        page.waitForTimeout(randomBetween(300, 600));

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
        humanType(page, password);
        page.waitForTimeout(randomBetween(500, 1000));

        try {
            ElementHandle checkbox = loginFrame.querySelector("#fm-agreement-checkbox, input[type=\"checkbox\"]");
            if (checkbox != null && checkbox.isVisible()) {
                if (!checkbox.isChecked()) {
                    checkbox.click();
                    page.waitForTimeout(randomBetween(300, 600));
                }
            }
        } catch (Exception ignored) {}

        log.info("[PasswordLogin] 点击登录按钮");
        if (!pageHelper.clickSubmit(loginFrame)) {
            ElementHandle passwordInput2 = pageHelper.findPasswordInput(loginFrame);
            if (passwordInput2 != null) passwordInput2.press("Enter");
        }
        page.waitForTimeout(randomBetween(3000, 5000));

        handleKeepLoginDialog(page);
        handleSliderWithRetry(page, loginFrame, accountId, 3);

        String cookieText = waitForLoginSuccess(page, context, accountId);
        if (cookieText != null) {
            log.info("[PasswordLogin] 账密登录成功: accountId={}, cookieLength={}", accountId, cookieText.length());
            notifyVerificationSuccess(accountId);
            return cookieText;
        }

        log.warn("[PasswordLogin] 登录等待超时，当前URL: {}", page.url());
        notifyManualVerification(page, accountId, "登录人工验证", "账密登录未完成，请在截图页面中完成扫码、人脸或短信验证");
        return null;
    }

    /**
     * 有头模式反检测脚本：仅清除 Playwright/WebDriver 痕迹，不覆盖 fingerprint-chromium 已在 C++ 层接管的属性。
     */
    private String headfulStealthScript() {
        return """
                (() => {
                    // 1. navigator.webdriver = false（真实 Chrome 返回 false 而非 undefined）
                    Object.defineProperty(Navigator.prototype, 'webdriver', {
                        get: () => false, configurable: true
                    });

                    // 2. 清除 Playwright/WebDriver 全局变量
                    [
                        'playwright', '__playwright', '__pw_manual', '__pw_original', 'webdriver',
                        '__webdriver_script_fn', '__webdriver_evaluate', '__webdriver_unwrapped',
                        '__fxdriver_evaluate', '__driver_evaluate', '__webdriver_script_func',
                        '_selenium', '_phantom', 'callPhantom', 'phantom',
                        '__playwright_evaluation_script__', '__pw_d'
                    ].forEach((key) => {
                        try { delete window[key]; } catch (e) {}
                    });

                    // 3. chrome.runtime 补全（fingerprint-chromium 不一定注入此对象）
                    if (!window.chrome) window.chrome = {};
                    window.chrome.runtime = window.chrome.runtime || {};
                    window.chrome.app = window.chrome.app || {
                        InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
                        RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
                        getDetails: () => null,
                        getIsInstalled: () => false,
                        runningState: () => 'cannot_run'
                    };
                    window.chrome.csi = window.chrome.csi || (() => ({}));
                    window.chrome.loadTimes = window.chrome.loadTimes || (() => ({}));

                    // 4. CDP stack trace 过滤：隐藏 addScriptToEvaluateOnNewDocument 注入痕迹
                    try {
                        const origPrepare = Error.prepareStackTrace;
                        Error.prepareStackTrace = function(err, stack) {
                            const filtered = stack.filter(f => {
                                const fn = f.getFileName() || '';
                                return !fn.includes('__playwright') && !fn.includes('pptr:');
                            });
                            if (origPrepare) return origPrepare(err, filtered);
                            return filtered.map(f => '    at ' + f.toString()).join('\\n');
                        };
                    } catch (e) {}

                    // 5. Function.prototype.toString 保护：让被覆盖的属性看起来是 native code
                    const nativeToString = Function.prototype.toString;
                    const overrides = new Set();
                    const originalDefineProperty = Object.defineProperty;
                    Object.defineProperty = function(target, key, descriptor) {
                        if (descriptor && descriptor.get && (target === Navigator.prototype || target === window)) {
                            overrides.add(descriptor.get);
                        }
                        return originalDefineProperty.call(this, target, key, descriptor);
                    };
                    Function.prototype.toString = function() {
                        if (overrides.has(this)) {
                            return 'function ' + (this.name || '') + '() { [native code] }';
                        }
                        return nativeToString.call(this);
                    };
                    overrides.add(Function.prototype.toString);
                })();
                """;
    }

    /**
     * 逐字符输入，模拟真人打字节奏（每个字符触发独立的 keydown/keypress/input/keyup 事件）。
     * Playwright 的 page.keyboard().type() 会为每个字符分别派发完整键盘事件链。
     */
    private void humanType(Page page, String text) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < text.length(); i++) {
            page.keyboard().press(String.valueOf(text.charAt(i)));
            long baseDelay = rng.nextLong(80, 200);
            // 偶尔模拟短暂停顿（思考/找键）
            if (rng.nextDouble() < 0.12) {
                baseDelay += rng.nextLong(100, 350);
            }
            page.waitForTimeout(baseDelay);
        }
    }

    /**
     * 检测并处理"保持登录状态"对话框，点击"保持"按钮。
     * 该对话框在密码登录成功后出现，有4秒自动超时（默认选"不保持"）。
     */
    private void handleKeepLoginDialog(Page page) {
        try {
            for (int i = 0; i < 3; i++) {
                // 使用 locator 在所有 frame 中查找文本精确为"保持"的按钮
                for (com.microsoft.playwright.Frame frame : page.frames()) {
                    try {
                        // 先检查是否有"保持登录"相关文本
                        com.microsoft.playwright.Locator dialog = frame.locator("text=保持登录状态");
                        if (dialog.count() == 0) continue;

                        // 找到对话框，定位"保持"按钮（排除"不保持"）
                        com.microsoft.playwright.Locator keepBtn = frame.locator("button:text-is('保持')");
                        if (keepBtn.count() == 0) {
                            keepBtn = frame.locator("[role='button']:text-is('保持')");
                        }
                        if (keepBtn.count() == 0) {
                            keepBtn = frame.locator("a:text-is('保持')");
                        }
                        if (keepBtn.count() > 0 && keepBtn.first().isVisible()) {
                            log.info("[PasswordLogin] 检测到'保持登录状态'对话框，点击'保持'按钮");
                            keepBtn.first().click();
                            page.waitForTimeout(randomBetween(1000, 2000));
                            return;
                        }
                    } catch (Exception ignored) {}
                }
                page.waitForTimeout(1000);
            }
            log.debug("[PasswordLogin] 未检测到'保持登录状态'对话框，继续流程");
        } catch (Exception e) {
            log.debug("[PasswordLogin] 处理'保持登录状态'对话框异常: {}", e.getMessage());
        }
    }

    private long randomBetween(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    @lombok.Value
    public static class ManualVerificationConfirmResult {
        boolean success;
        String cookieText;
        String message;

        static ManualVerificationConfirmResult success(String cookieText) {
            return new ManualVerificationConfirmResult(true, cookieText, "人工验证已确认，Cookie 已读取");
        }

        static ManualVerificationConfirmResult failed(String message) {
            return new ManualVerificationConfirmResult(false, null, message);
        }
    }

    private static class PendingManualVerification {
        private final Long accountId;
        private final Page page;
        private final BrowserContext context;
        private volatile long expiresAt;
        private volatile String confirmedCookieText;
        private volatile long confirmedAt;
        private volatile String verificationType = "人工验证";
        private volatile String detail = "";
        private volatile String message = "账号登录需要人工处理";

        private PendingManualVerification(Long accountId, Page page, BrowserContext context, long expiresAt) {
            this.accountId = accountId;
            this.page = page;
            this.context = context;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        private boolean isClosed() {
            try {
                return page == null || page.isClosed() || context == null;
            } catch (Exception e) {
                return true;
            }
        }
    }

    @lombok.Value
    public static class ManualVerificationState {
        Long accountId;
        String verificationType;
        String message;
        String detail;
        String screenshotUrl;
        long expiresAt;
        int timeoutSeconds;
    }
}
