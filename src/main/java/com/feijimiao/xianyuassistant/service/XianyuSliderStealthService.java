package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.config.SliderProperties;
import com.feijimiao.xianyuassistant.sse.SseEventBus;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.HttpHeader;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class XianyuSliderStealthService {
    private static final String HOMEPAGE_URL = "https://www.goofish.com";
    private static final String IM_URL = "https://www.goofish.com/im";
    private static final long POST_SUCCESS_SETTLE_MS = 2500L;
    private static final String DEFAULT_CAPTCHA_DEBUG_DIR = "dbdata/captcha-debug";

    private final SliderAutoVerifyService sliderAutoVerifyService;
    private final SliderPageInspector sliderPageInspector;
    private final CaptchaCookieMergeService captchaCookieMergeService;
    private final SliderCookieRefreshVerifier sliderCookieRefreshVerifier;
    private final SliderBrowserFingerprintService sliderBrowserFingerprintService;
    private final SliderCookieStabilizerService sliderCookieStabilizerService;

    private SliderProperties sliderProperties;

    @Autowired(required = false)
    private CaptchaDebugImageService captchaDebugImageService;

    @Autowired(required = false)
    private SseEventBus sseEventBus;

    @Autowired(required = false)
    void setSliderProperties(SliderProperties sliderProperties) {
        this.sliderProperties = sliderProperties;
    }

    public SliderVerificationResult verify(String initialCookieText, String targetUrl) {
        return verify(null, initialCookieText, targetUrl);
    }

    public SliderVerificationResult verify(Long accountId, String initialCookieText, String targetUrl) {
        return verify(accountId, initialCookieText, targetUrl, true);
    }

    public SliderVerificationResult verify(Long accountId, String initialCookieText, String targetUrl,
                                           boolean allowManualRecovery) {
        return runVerification(accountId, initialCookieText, targetUrl, allowManualRecovery);
    }

    private SliderVerificationResult runVerification(Long accountId, String initialCookieText, String targetUrl,
                                                     boolean allowManualRecovery) {
        Map<String, String> observedSetCookieUpdates = new LinkedHashMap<>();
        SliderBrowserFingerprintService.BrowserProfile browserProfile =
                sliderBrowserFingerprintService.profile(accountId);
        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create();
             BrowserSession session = launchBrowserSession(playwright, accountId, browserProfile)) {
            BrowserContext context = session.getContext();
            boolean isPunishUrl = targetUrl != null
                    && (targetUrl.contains("punish") || targetUrl.contains("x5secdata"));
            // punish URL 场景将导航到 /im 让滑块以弹窗形式触发，
            // fullStealthScript 会导致 /im SPA 白屏，仅用 CDP 最小脚本
            if (!isPunishUrl) {
                context.addInitScript(sliderBrowserFingerprintService.stealthScript(browserProfile));
            }
            injectInitialCookies(context, initialCookieText);
            Page page = context.newPage();
            // connectOverCDP 模式下 addInitScript 对 iframe 无效，通过 CDP 原生协议注入
            injectStealthViaCDP(page);
            if (session.getExternalLauncher() == null) {
                sliderBrowserFingerprintService.applyNetworkFingerprint(context, page, browserProfile);
            }
            Consumer<Response> responseListener =
                    response -> collectSetCookieUpdates(response, observedSetCookieUpdates);
            context.onResponse(responseListener);
            try {
                return runPageVerification(
                        accountId, page, context, initialCookieText, targetUrl,
                        observedSetCookieUpdates, allowManualRecovery);
            } finally {
                context.offResponse(responseListener);
            }
        } catch (Exception e) {
            log.error("执行完整滑块验证链路失败", e);
            return SliderVerificationResult.failed("执行完整滑块验证链路异常: " + e.getMessage());
        }
    }

    private SliderVerificationResult runPageVerification(Long accountId,
                                                         Page page,
                                                         BrowserContext context,
                                                         String initialCookieText,
                                                         String targetUrl,
                                                         Map<String, String> observedSetCookieUpdates,
                                                         boolean allowManualRecovery) {
        warmupContext(page, targetUrl);
        navigateToCaptchaPage(page, targetUrl);

        // punish URL 场景导航到 /im：如果 /im 页面已正常加载（有登录态），直接提取 cookie 返回
        boolean isPunishUrl = targetUrl != null
                && (targetUrl.contains("punish") || targetUrl.contains("x5secdata"));
        if (isPunishUrl) {
            String jsCookie = extractPageCookie(page);
            if (jsCookie != null && !jsCookie.isBlank() && jsCookie.contains("unb=")) {
                log.info("[SliderStealth] /im页面已有登录态，直接提取Cookie返回: accountId={}, length={}",
                        accountId, jsCookie.length());
                return SliderVerificationResult.success(jsCookie, null, java.util.Collections.emptyMap());
            }
        }

        simulateHumanWarmup(page);
        Map<String, String> cookieBaseline = sliderCookieRefreshVerifier.snapshotCookies(
                context, page, observedSetCookieUpdates);
        if (!solveWithOuterReloadRetry(page, accountId, targetUrl)) {
            if (allowManualRecovery) {
                // 滑块失败后，检测是否有二维码/人脸验证入口可以恢复
                SliderVerificationResult recoveryResult = attemptPostFailureRecovery(
                        accountId, page, context, initialCookieText, cookieBaseline, observedSetCookieUpdates);
                if (recoveryResult != null) {
                    return recoveryResult;
                }
            }
            return SliderVerificationResult.failed("自动滑块执行失败");
        }
        waitForPostSuccessSettle(page);
        SliderPageInspector.PageState state = snapshotPageState(page);
        if (!sliderPageInspector.isVerificationSuccess(state)) {
            return SliderVerificationResult.failed("滑块后页面仍处于验证链路");
        }
        return collectSuccessCookies(context, page, initialCookieText, cookieBaseline, observedSetCookieUpdates);
    }

    private SliderVerificationResult attemptPostFailureRecovery(Long accountId,
                                                                 Page page,
                                                                 BrowserContext context,
                                                                 String initialCookieText,
                                                                 Map<String, String> cookieBaseline,
                                                                 Map<String, String> observedSetCookieUpdates) {
        if (page == null || page.isClosed()) return null;
        try {
            // 检测页面是否有二维码验证入口
            boolean hasQrCode = detectQrCodeVerification(page);
            if (!hasQrCode) {
                log.debug("[SliderRecovery] 未检测到二维码验证入口，无法恢复");
                return null;
            }
            log.info("[SliderRecovery] 检测到二维码验证页面，等待人工扫码验证: accountId={}", accountId);
            // 截图保存供前端展示
            captureQrCodeScreenshot(page, accountId);
            notifyManualVerification(accountId, "扫码/二维码验证", "自动滑块失败，请在前端弹窗中扫码或完成页面验证");
            // 轮询等待验证完成（最多 120 秒）
            boolean verified = waitForManualVerification(context, page, accountId, 120);
            if (!verified) {
                log.warn("[SliderRecovery] 等待人工验证超时: accountId={}", accountId);
                return null;
            }
            log.info("[SliderRecovery] 人工验证完成，收集 Cookie: accountId={}", accountId);
            waitForPostSuccessSettle(page);
            return collectSuccessCookies(context, page, initialCookieText, cookieBaseline, observedSetCookieUpdates);
        } catch (Exception e) {
            log.warn("[SliderRecovery] 恢复流程异常: {}", e.getMessage());
            return null;
        }
    }

    private boolean detectQrCodeVerification(Page page) {
        try {
            String bodyText = page.textContent("body");
            boolean hasKeyword = bodyText != null && (
                    bodyText.contains("扫码验证") ||
                    bodyText.contains("请使用") ||
                    bodyText.contains("二维码") ||
                    bodyText.contains("点我反馈") ||
                    bodyText.contains("页面访问出现了问题"));
            boolean hasQrElement = page.locator(".bx-pu-qrcode-wrap, .captcha-qrcode, img[src*='qrcode'], canvas").count() > 0;
            return hasKeyword || hasQrElement;
        } catch (Exception e) {
            return false;
        }
    }

    private void captureQrCodeScreenshot(Page page, Long accountId) {
        try {
            Path dir = Path.of(resolveCaptchaDebugDirectory(), String.valueOf(accountId));
            Files.createDirectories(dir);
            String filename = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_qrcode.png";
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(dir.resolve(filename))
                    .setFullPage(true));
            log.info("[SliderRecovery] 二维码截图已保存: {}", dir.resolve(filename));
        } catch (Exception e) {
            log.warn("[SliderRecovery] 截图失败: {}", e.getMessage());
        }
    }

    private String resolveCaptchaDebugDirectory() {
        if (sliderProperties == null
                || sliderProperties.getFailureCapture() == null
                || sliderProperties.getFailureCapture().getDirectory() == null
                || sliderProperties.getFailureCapture().getDirectory().isBlank()) {
            return DEFAULT_CAPTCHA_DEBUG_DIR;
        }
        return sliderProperties.getFailureCapture().getDirectory();
    }

    private void notifyManualVerification(Long accountId, String verificationType, String detail) {
        if (accountId == null || sseEventBus == null) {
            return;
        }
        String screenshotUrl = captchaDebugImageService == null ? ""
                : captchaDebugImageService.latestImageUrl(accountId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", accountId);
        payload.put("type", "verification_required");
        payload.put("verificationType", verificationType);
        payload.put("detail", detail == null ? "" : detail);
        payload.put("message", "账号" + accountId + "需要" + verificationType + "，请在前端弹窗中处理");
        payload.put("screenshotUrl", screenshotUrl);
        payload.put("captchaImageUrl", screenshotUrl);
        sseEventBus.broadcast("notification", payload);
    }

    private boolean waitForManualVerification(BrowserContext context, Page page, Long accountId, int maxWaitSeconds) {
        long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        List<String> required = CaptchaCookieMergeService.REQUIRED_FIELDS;
        long pollIntervalMs = 3000L;
        long lastMissingLogMs = 0L;

        // 先拍一个 baseline 快照：持久化 profile 中可能已有旧 Cookie，
        // 必须检测到 Cookie 值发生变化才算验证通过，而不是仅检查存在性。
        Map<String, String> baseline = snapshotContextCookies(context);
        Map<String, String> baselineRequired = new LinkedHashMap<>();
        for (String name : required) {
            String v = baseline.get(name);
            if (v != null && !v.isBlank()) {
                baselineRequired.put(name, v);
            }
        }
        boolean baselineHasAll = baselineRequired.size() == required.size();

        while (System.currentTimeMillis() < deadline) {
            try {
                page.waitForTimeout(pollIntervalMs);
                if (page.isClosed()) {
                    log.warn("[SliderRecovery] 等待人工验证期间页面被关闭: accountId={}", accountId);
                    return false;
                }
                Map<String, String> cookies = snapshotContextCookies(context);
                List<String> missing = new ArrayList<>();
                for (String name : required) {
                    String v = cookies.get(name);
                    if (v == null || v.isBlank()) {
                        missing.add(name);
                    }
                }
                if (missing.isEmpty()) {
                    if (!baselineHasAll) {
                        // baseline 缺少某些字段，现在全部出现了 → 确认验证通过
                        log.info("[SliderRecovery] 人工验证完成（检测到全部核心登录 Cookie，baseline 缺失已补齐）: accountId={}", accountId);
                        return true;
                    }
                    // baseline 已有全部字段，需要检测值是否变化
                    boolean anyChanged = false;
                    for (String name : required) {
                        String oldVal = baselineRequired.get(name);
                        String newVal = cookies.get(name);
                        if (oldVal != null && newVal != null && !oldVal.equals(newVal)) {
                            anyChanged = true;
                            log.info("[SliderRecovery] Cookie '{}' 值已变化，确认人工验证通过: accountId={}", name, accountId);
                            break;
                        }
                    }
                    if (anyChanged) {
                        log.info("[SliderRecovery] 人工验证完成（核心 Cookie 值已刷新）: accountId={}", accountId);
                        return true;
                    }
                }
                long now = System.currentTimeMillis();
                if (now - lastMissingLogMs > 15000L) {
                    long remaining = (deadline - now) / 1000L;
                    if (!missing.isEmpty()) {
                        log.info("[SliderRecovery] 等待人工扫码完成: accountId={}, 缺失={}, 剩余{}s",
                                accountId, missing, remaining);
                    } else {
                        log.info("[SliderRecovery] 等待人工扫码完成: accountId={}, Cookie存在但值未变化, 剩余{}s",
                                accountId, remaining);
                    }
                    lastMissingLogMs = now;
                }
            } catch (Exception e) {
                log.warn("[SliderRecovery] 等待人工验证轮询异常: accountId={}, error={}", accountId, e.getMessage());
                return false;
            }
        }
        log.warn("[SliderRecovery] 等待人工验证超时（{}s 内未拿到核心登录 Cookie）: accountId={}",
                maxWaitSeconds, accountId);
        return false;
    }

    private Map<String, String> snapshotContextCookies(BrowserContext context) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            for (Cookie cookie : context.cookies()) {
                if (cookie.name == null) continue;
                result.putIfAbsent(cookie.name, cookie.value == null ? "" : cookie.value);
            }
        } catch (Exception e) {
            log.debug("[SliderRecovery] 读取 context cookies 失败: {}", e.getMessage());
        }
        return result;
    }

    private SliderVerificationResult collectSuccessCookies(BrowserContext context,
                                                            Page page,
                                                            String initialCookieText,
                                                            Map<String, String> cookieBaseline,
                                                            Map<String, String> observedSetCookieUpdates) {
        Map<String, String> stabilizedCookies = sliderCookieStabilizerService.stabilize(
                context, page, Map.of(), observedSetCookieUpdates);
        SliderCookieRefreshVerifier.CookieRefreshCheck cookieCheck =
                sliderCookieRefreshVerifier.verify(context, page, cookieBaseline, observedSetCookieUpdates);
        if (!cookieCheck.isAccepted()) {
            log.warn("验证成功后 Cookie 刷新校验失败: {}", cookieCheck.getMessage());
            return SliderVerificationResult.failed("验证后 Cookie 未确认刷新");
        }
        log.info("验证成功后 Cookie 刷新校验通过: confirmed={}, softSuccess={}, message={}",
                cookieCheck.getCookieRefreshConfirmed(),
                cookieCheck.isSoftSuccess(),
                cookieCheck.getMessage());
        Map<String, String> browserCookies = new LinkedHashMap<>(stabilizedCookies);
        browserCookies.putAll(cookieCheck.getCurrentCookies());
        CaptchaCookieMergeService.CookieMergeResult mergeResult =
                captchaCookieMergeService.merge(initialCookieText, browserCookies, observedSetCookieUpdates);
        if (!mergeResult.getMissingRequiredFields().isEmpty()) {
            log.warn("验证成功后 Cookie 仍缺失核心字段: {}", mergeResult.getMissingRequiredFields());
            return SliderVerificationResult.failed("验证后 Cookie 缺失核心字段");
        }
        return SliderVerificationResult.success(
                captchaCookieMergeService.formatCookieText(mergeResult.getMergedCookies()),
                mergeResult,
                observedSetCookieUpdates
        );
    }

    private boolean solveWithOuterReloadRetry(Page page, Long accountId, String targetUrl) {
        int outerAttempts = resolveOuterReloadAttempts();
        for (int outer = 1; outer <= outerAttempts; outer++) {
            SliderAutoVerifyResult result = sliderAutoVerifyService.solveDetailed(page, accountId);
            if (result.isSuccess()) {
                if (outer > 1) {
                    log.info("自动滑块在第{}次外层 reload 后通过: accountId={}", outer, accountId);
                }
                return true;
            }
            if (result.isHardBlock()) {
                log.warn("自动滑块命中硬拦截，跳过外层 reload 重试: accountId={}, message={}",
                        accountId, result.getMessage());
                return false;
            }
            if (outer >= outerAttempts) {
                break;
            }
            log.warn("自动滑块外层第{}/{}轮失败，准备 reload 后重试: accountId={}",
                    outer, outerAttempts, accountId);
            if (!reloadForOuterRetry(page, targetUrl)) {
                return false;
            }
        }
        return false;
    }

    private int resolveOuterReloadAttempts() {
        if (sliderProperties == null || sliderProperties.getRetry() == null) {
            return 3;
        }
        return Math.max(1, sliderProperties.getRetry().getOuterReloadAttempts());
    }

    private boolean reloadForOuterRetry(Page page, String targetUrl) {
        if (page == null || page.isClosed()) {
            return false;
        }
        long minDelay = 2000L;
        long maxDelay = 4000L;
        if (sliderProperties != null && sliderProperties.getRetry() != null) {
            minDelay = Math.max(0L, sliderProperties.getRetry().getOuterReloadDelayMinMs());
            maxDelay = Math.max(minDelay + 1L, sliderProperties.getRetry().getOuterReloadDelayMaxMs());
        }
        try {
            page.waitForTimeout(ThreadLocalRandom.current().nextLong(minDelay, maxDelay));
            page.reload(new Page.ReloadOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(20000));
            simulateHumanWarmup(page);
            return true;
        } catch (Exception e) {
            log.warn("外层 reload 兜底失败，尝试重新导航: {}", e.getMessage());
            try {
                page.navigate(targetUrl, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(20000));
                simulateHumanWarmup(page);
                return true;
            } catch (Exception inner) {
                log.error("外层 reload 兜底重新导航也失败，放弃: {}", inner.getMessage());
                return false;
            }
        }
    }

    private BrowserSession launchBrowserSession(
            com.microsoft.playwright.Playwright playwright,
            Long accountId,
            SliderBrowserFingerprintService.BrowserProfile browserProfile
    ) {
        if (accountId != null) {
            try {
                Path profileDir = accountProfileDir(accountId);
                List<String> args = sliderBrowserFingerprintService.buildLaunchArgs(browserProfile);
                args = new ArrayList<>(args);
                args.add("--remote-allow-origins=*");
                ExternalBrowserLauncher launcher = ExternalBrowserLauncher.launch(args, profileDir, accountId);
                Browser browser = playwright.chromium().connectOverCDP(launcher.getCdpUrl());
                BrowserContext context = browser.contexts().isEmpty()
                        ? browser.newContext() : browser.contexts().get(0);
                log.info("账号{}滑块浏览器已使用 connectOverCDP (绕过 Runtime.Enable): profileDir={}", accountId, profileDir);
                return BrowserSession.external(browser, context, launcher);
            } catch (Exception e) {
                log.warn("账号{} connectOverCDP 启动失败，回退 launchPersistentContext: {}",
                        accountId, e.getMessage());
            }
            try {
                Path profileDir = accountProfileDir(accountId);
                BrowserContext context = playwright.chromium()
                        .launchPersistentContext(profileDir, buildPersistentContextOptions(browserProfile));
                log.info("账号{}滑块浏览器回退 persistent profile: {}", accountId, profileDir);
                return BrowserSession.persistent(context);
            } catch (Exception e) {
                log.warn("账号{} persistent profile 启动也失败，回退干净种子上下文: {}",
                        accountId, e.getMessage());
            }
        }
        Browser browser = playwright.chromium().launch(buildLaunchOptions(browserProfile));
        BrowserContext context = browser.newContext(buildContextOptions(browserProfile));
        return BrowserSession.regular(browser, context);
    }

    private Path accountProfileDir(Long accountId) throws Exception {
        Path baseDir = Path.of(System.getProperty("user.dir"), "browser_data");
        Path profileDir = baseDir.resolve("user_" + accountId);
        Files.createDirectories(profileDir);
        return profileDir;
    }

    private void injectInitialCookies(BrowserContext context, String initialCookieText) {
        List<Cookie> initialCookies = buildSeedCookies(initialCookieText);
        if (!initialCookies.isEmpty()) {
            context.addCookies(initialCookies);
            log.info("滑块上下文已注入 {} 个历史 Cookie", initialCookies.size());
        }
    }

    private BrowserType.LaunchPersistentContextOptions buildPersistentContextOptions(
            SliderBrowserFingerprintService.BrowserProfile browserProfile
    ) {
        BrowserType.LaunchPersistentContextOptions options =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(shouldUseHeadless())
                        .setIgnoreDefaultArgs(List.of("--enable-automation"))
                        .setArgs(sliderBrowserFingerprintService.buildLaunchArgs(browserProfile))
                        .setViewportSize(browserProfile.getViewportWidth(), browserProfile.getViewportHeight())
                        .setScreenSize(browserProfile.getViewportWidth(), browserProfile.getViewportHeight())
                        .setDeviceScaleFactor(browserProfile.getDeviceScaleFactor())
                        .setIsMobile(false)
                        .setHasTouch(false)
                        .setUserAgent(browserProfile.getUserAgent())
                        .setLocale(browserProfile.getLocale())
                        .setTimezoneId(browserProfile.getTimezoneId())
                        .setAcceptDownloads(true)
                        .setIgnoreHTTPSErrors(true)
                        .setExtraHTTPHeaders(sliderBrowserFingerprintService.extraHeaders(browserProfile));
        resolveProxy().ifPresent(options::setProxy);
        PlaywrightBrowserUtils.resolveChromiumPath().ifPresent(options::setExecutablePath);
        return options;
    }

    private BrowserType.LaunchOptions buildLaunchOptions(
            SliderBrowserFingerprintService.BrowserProfile browserProfile
    ) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(shouldUseHeadless())
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(sliderBrowserFingerprintService.buildLaunchArgs(browserProfile));
        resolveProxy().ifPresent(options::setProxy);
        PlaywrightBrowserUtils.resolveChromiumPath().ifPresent(options::setExecutablePath);
        return options;
    }

    private Browser.NewContextOptions buildContextOptions(
            SliderBrowserFingerprintService.BrowserProfile browserProfile
    ) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(browserProfile.getViewportWidth(), browserProfile.getViewportHeight())
                .setScreenSize(browserProfile.getViewportWidth(), browserProfile.getViewportHeight())
                .setDeviceScaleFactor(browserProfile.getDeviceScaleFactor())
                .setIsMobile(false)
                .setHasTouch(false)
                .setUserAgent(browserProfile.getUserAgent())
                .setLocale(browserProfile.getLocale())
                .setTimezoneId(browserProfile.getTimezoneId())
                .setExtraHTTPHeaders(sliderBrowserFingerprintService.extraHeaders(browserProfile));
        resolveProxy().ifPresent(options::setProxy);
        return options;
    }

    private java.util.Optional<Proxy> resolveProxy() {
        if (sliderProperties == null || sliderProperties.getProxy() == null) {
            return java.util.Optional.empty();
        }
        SliderProperties.Proxy config = sliderProperties.getProxy();
        if (!config.isEnabled()) {
            return java.util.Optional.empty();
        }
        String server = buildProxyServer(config);
        if (server == null || server.isBlank()) {
            log.warn("slider.proxy.enabled=true 但未提供有效 server/host:port，跳过代理注入");
            return java.util.Optional.empty();
        }
        Proxy proxy = new Proxy(server);
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            proxy.setUsername(config.getUsername());
        }
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            proxy.setPassword(config.getPassword());
        }
        if (config.getBypass() != null && !config.getBypass().isBlank()) {
            proxy.setBypass(config.getBypass());
        }
        log.info("滑块浏览器启用代理: server={}", server);
        return java.util.Optional.of(proxy);
    }

    private boolean shouldUseHeadless() {
        String display = System.getenv("DISPLAY");
        boolean headless = shouldUseHeadless(display);
        if (!headless) {
            log.info("检测到 DISPLAY={}，使用有头模式 (headful + Xvfb)", display);
        }
        return headless;
    }

    static boolean shouldUseHeadless(String display) {
        return display == null || display.isBlank();
    }

    private String buildProxyServer(SliderProperties.Proxy config) {
        if (config.getServer() != null && !config.getServer().isBlank()) {
            return config.getServer().trim();
        }
        if (config.getHost() == null || config.getHost().isBlank() || config.getPort() <= 0) {
            return null;
        }
        String type = config.getType() == null || config.getType().isBlank()
                ? "http"
                : config.getType().trim().toLowerCase();
        return type + "://" + config.getHost().trim() + ":" + config.getPort();
    }

    private List<Cookie> buildSeedCookies(String cookieText) {
        List<Cookie> cookies = new ArrayList<>();
        Map<String, String> cookieMap = captchaCookieMergeService.parseCookieText(cookieText);
        cookieMap.forEach((name, value) -> {
            cookies.add(new Cookie(name, value).setDomain(".goofish.com").setPath("/"));
            cookies.add(new Cookie(name, value).setDomain(".taobao.com").setPath("/"));
        });
        return cookies;
    }

    private void collectSetCookieUpdates(Response response, Map<String, String> updates) {
        try {
            List<String> headers = response.headerValues("set-cookie");
            if (headers == null || headers.isEmpty()) {
                headers = new ArrayList<>();
                for (HttpHeader header : response.headersArray()) {
                    if ("set-cookie".equalsIgnoreCase(header.name)) {
                        headers.add(header.value);
                    }
                }
            }
            updates.putAll(captchaCookieMergeService.extractSetCookieUpdates(headers));
        } catch (Exception e) {
            log.debug("捕获响应 Set-Cookie 失败: {}", e.getMessage());
        }
    }

    private void warmupContext(Page page, String targetUrl) {
        for (String url : List.of(HOMEPAGE_URL, IM_URL)) {
            if (url.equals(targetUrl)) {
                continue;
            }
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(15000));
                page.waitForTimeout(randomBetween(800, 1500));
                page.mouse().move(randomBetween(260, 980), randomBetween(180, 620));
            } catch (Exception e) {
                log.debug("滑块预热访问失败: url={}, error={}", url, e.getMessage());
            }
        }
    }

    private void navigateToCaptchaPage(Page page, String targetUrl) {
        boolean isPunishUrl = targetUrl != null
                && (targetUrl.contains("punish") || targetUrl.contains("x5secdata"));
        if (isPunishUrl) {
            // punish URL 直接导航会呈现全页面滑块（和正常用户体验不同，易被检测）。
            // 导航到 /im，然后通过 fetch 触发 token 请求让滑块弹窗自然出现。
            log.info("检测到punish URL，导航到/im让滑块弹窗自然触发");
            try {
                page.navigate(IM_URL, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30000));
                page.waitForTimeout(randomBetween(3000, 5000));
                // 在 /im 页面内 fetch token API 触发滑块弹窗
                triggerSliderViaFetch(page);
                // 轮询等待滑块弹窗出现
                boolean sliderAppeared = waitForSliderPopup(page, 20000);
                if (sliderAppeared) {
                    log.info("/im页面滑块弹窗已出现");
                } else {
                    log.warn("/im页面20秒内未出现滑块弹窗，继续尝试后续流程");
                }
            } catch (Exception e) {
                log.warn("/im页面加载失败: {}", e.getMessage());
            }
            return;
        }
        try {
            page.navigate(targetUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(30000));
        } catch (Exception e) {
            log.warn("验证页加载异常，继续尝试后续流程: {}", e.getMessage());
            page.waitForTimeout(2000);
        }
    }

    /**
     * 在 /im 页面内请求 token API 触发服务端返回 punish 验证，
     * 页面 JS 会拦截并弹出滑块模态框。
     */
    private void triggerSliderViaFetch(Page page) {
        try {
            page.evaluate(
                    "() => fetch('https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/"
                            + "?jsv=2.7.2&appKey=34839810&type=originaljson&dataType=json&v=1.0"
                            + "&api=mtop.taobao.idlemessage.pc.login.token&sessionOption=AutoLoginOnly',"
                            + " {credentials:'include'}).catch(() => null)");
        } catch (Exception e) {
            log.debug("triggerSliderViaFetch异常（预期内）: {}", e.getMessage());
        }
    }

    private String extractPageCookie(Page page) {
        try {
            // 先尝试 document.cookie（非httpOnly的cookie）
            Object jsResult = page.evaluate("() => document.cookie");
            String jsCookie = jsResult != null ? jsResult.toString() : "";

            // 补充 context.cookies()（含httpOnly的cookie）
            StringBuilder sb = new StringBuilder(jsCookie);
            try {
                var contextCookies = page.context().cookies();
                for (var c : contextCookies) {
                    String pair = c.name + "=" + c.value;
                    if (!sb.toString().contains(c.name + "=")) {
                        if (!sb.isEmpty()) sb.append("; ");
                        sb.append(pair);
                    }
                }
            } catch (Exception ignored) {}

            // 再通过 CDP 获取完整 cookie（connectOverCDP 模式下 context.cookies 可能不完整）
            try {
                CDPSession cdp = page.context().newCDPSession(page);
                try {
                    var result = cdp.send("Network.getAllCookies");
                    if (result != null) {
                        var cookies = result.getAsJsonArray("cookies");
                        if (cookies != null) {
                            for (var elem : cookies) {
                                var obj = elem.getAsJsonObject();
                                String name = obj.get("name").getAsString();
                                String value = obj.get("value").getAsString();
                                if (!sb.toString().contains(name + "=")) {
                                    if (!sb.isEmpty()) sb.append("; ");
                                    sb.append(name).append("=").append(value);
                                }
                            }
                        }
                    }
                } finally {
                    try { cdp.detach(); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            String cookie = sb.toString();
            log.info("[SliderStealth] extractPageCookie: length={}, hasUnb={}", cookie.length(), cookie.contains("unb="));
            return cookie.isBlank() ? null : cookie;
        } catch (Exception e) {
            log.debug("[SliderStealth] extractPageCookie异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 轮询等待滑块弹窗出现（/im SPA 动态渲染，需要等 JS 执行完毕）。
     */
    private boolean waitForSliderPopup(Page page, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String[] sliderSelectors = {"#nc_1_n1z", ".nc-container", ".nc_scale", ".btn_slide", ".sm-btn"};
        while (System.currentTimeMillis() < deadline) {
            for (com.microsoft.playwright.Frame frame : page.frames()) {
                for (String selector : sliderSelectors) {
                    try {
                        var el = frame.querySelector(selector);
                        if (el != null && el.isVisible()) {
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
            }
            page.waitForTimeout(800);
        }
        return false;
    }

    private void simulateHumanWarmup(Page page) {
        page.waitForTimeout(randomBetween(2800, 4200));
        int moveCount = randomInt(2, 4);
        for (int i = 0; i < moveCount; i++) {
            page.mouse().move(randomBetween(320, 1220), randomBetween(220, 700));
            page.waitForTimeout(randomBetween(80, 220));
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.35) {
            page.mouse().wheel(0, randomBetween(60, 180));
            page.waitForTimeout(randomBetween(300, 600));
            if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                page.mouse().wheel(0, -randomBetween(40, 120));
                page.waitForTimeout(randomBetween(200, 400));
            }
        }
        page.waitForTimeout(randomBetween(350, 800));
    }

    private void waitForPostSuccessSettle(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
        } catch (Exception e) {
            log.debug("滑块成功后等待 networkidle 失败: {}", e.getMessage());
        }
        page.waitForTimeout(POST_SUCCESS_SETTLE_MS);
    }

    private SliderPageInspector.PageState snapshotPageState(Page page) {
        return SliderPageInspector.PageState.builder()
                .url(safeUrl(page))
                .title(safeTitle(page))
                .bodyText(safeBodyText(page))
                .visibleSlider(hasVisibleSlider(page))
                .failureSignal(hasFailureSignal(page))
                .build();
    }

    private boolean hasVisibleSlider(Page page) {
        for (String selector : List.of("#nc_1_n1z", ".btn_slide", ".sm-btn", ".sm-btn-wrapper", ".nc_scale")) {
            try {
                if (page.locator(selector).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean hasFailureSignal(Page page) {
        String body = safeBodyText(page).toLowerCase();
        return body.contains("验证失败")
                || body.contains("点击框体重试")
                || body.contains("请重试")
                || body.contains("failed");
    }

    private String safeUrl(Page page) {
        try {
            return page.url();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeTitle(Page page) {
        try {
            return page.title();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeBodyText(Page page) {
        try {
            return page.innerText("body", new Page.InnerTextOptions().setTimeout(1500));
        } catch (Exception e) {
            try {
                return page.content();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /**
     * 通过 CDP 原生协议注入 WebGL 伪装 + Playwright 痕迹清除脚本。
     * connectOverCDP 模式下 addInitScript 对 iframe 无效，必须用 CDP 注入。
     */
    private void injectStealthViaCDP(Page page) {
        CDPSession cdpSession = null;
        try {
            cdpSession = page.context().newCDPSession(page);
            com.google.gson.JsonObject params = new com.google.gson.JsonObject();
            params.addProperty("source", minimalCdpStealthScript());
            cdpSession.send("Page.addScriptToEvaluateOnNewDocument", params);
            log.info("[SliderStealth] CDP注入反检测脚本成功");
        } catch (Exception e) {
            log.warn("[SliderStealth] CDP注入失败: {}", e.getMessage());
        } finally {
            if (cdpSession != null) {
                try { cdpSession.detach(); } catch (Exception ignored) {}
            }
        }
    }

    private String minimalCdpStealthScript() {
        return """
                (() => {
                    const spoofRenderer = 'ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0, D3D11)';
                    const spoofVendor = 'Google Inc. (Intel)';
                    const getParameterProto = WebGLRenderingContext.prototype.getParameter;
                    const getParameter2Proto = (typeof WebGL2RenderingContext !== 'undefined')
                        ? WebGL2RenderingContext.prototype.getParameter : null;
                    function patchGetParameter(original) {
                        return function(param) {
                            const ext = this.getExtension('WEBGL_debug_renderer_info');
                            if (ext) {
                                if (param === ext.UNMASKED_RENDERER_WEBGL) return spoofRenderer;
                                if (param === ext.UNMASKED_VENDOR_WEBGL) return spoofVendor;
                            }
                            if (param === 0x9246) return spoofRenderer;
                            if (param === 0x9245) return spoofVendor;
                            return original.call(this, param);
                        };
                    }
                    WebGLRenderingContext.prototype.getParameter = patchGetParameter(getParameterProto);
                    if (getParameter2Proto) {
                        WebGL2RenderingContext.prototype.getParameter = patchGetParameter(getParameter2Proto);
                    }
                    Object.defineProperty(Navigator.prototype, 'webdriver', { get: () => false, configurable: true });
                    ['playwright','__playwright','__pw_manual','__pw_original','webdriver',
                     '__webdriver_script_fn','__webdriver_evaluate','__webdriver_unwrapped',
                     '__playwright_evaluation_script__','__pw_d'
                    ].forEach(key => { try { delete window[key]; } catch(e) {} });
                    const nativeToString = Function.prototype.toString;
                    const spoofed = new Set([
                        WebGLRenderingContext.prototype.getParameter,
                        getParameter2Proto ? WebGL2RenderingContext.prototype.getParameter : null
                    ].filter(Boolean));
                    Function.prototype.toString = function() {
                        if (spoofed.has(this)) return 'function getParameter() { [native code] }';
                        if (this === Function.prototype.toString) return 'function toString() { [native code] }';
                        return nativeToString.call(this);
                    };
                    spoofed.add(Function.prototype.toString);
                })();
                """;
    }

    private int randomBetween(int minInclusive, int maxExclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    @Value
    private static class BrowserSession implements AutoCloseable {
        Browser browser;
        BrowserContext context;
        ExternalBrowserLauncher externalLauncher;

        static BrowserSession persistent(BrowserContext context) {
            return new BrowserSession(null, context, null);
        }

        static BrowserSession regular(Browser browser, BrowserContext context) {
            return new BrowserSession(browser, context, null);
        }

        static BrowserSession external(Browser browser, BrowserContext context, ExternalBrowserLauncher launcher) {
            return new BrowserSession(browser, context, launcher);
        }

        @Override
        public void close() {
            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (browser != null && browser.isConnected()) {
                    browser.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (externalLauncher != null) {
                    externalLauncher.shutdown();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Value
    @Builder
    public static class SliderVerificationResult {
        boolean success;
        String cookieText;
        String message;
        CaptchaCookieMergeService.CookieMergeResult mergeResult;
        Map<String, String> responseCookieUpdates;

        public static SliderVerificationResult success(
                String cookieText,
                CaptchaCookieMergeService.CookieMergeResult mergeResult,
                Map<String, String> responseCookieUpdates
        ) {
            return SliderVerificationResult.builder()
                    .success(true)
                    .cookieText(cookieText)
                    .message("自动验证成功")
                    .mergeResult(mergeResult)
                    .responseCookieUpdates(new LinkedHashMap<>(responseCookieUpdates))
                    .build();
        }

        public static SliderVerificationResult failed(String message) {
            return SliderVerificationResult.builder()
                    .success(false)
                    .message(message)
                    .responseCookieUpdates(Map.of())
                    .build();
        }
    }
}
