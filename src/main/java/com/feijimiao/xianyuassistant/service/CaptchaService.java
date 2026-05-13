package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 滑块验证服务
 *
 * 策略：自动检测 + headless 自动滑块
 * 1. 自动检测是否需要滑块验证
 * 2. 尝试自动滑块验证
 * 3. 自动滑块失败后先尝试账密登录
 * 4. 账密登录失败后再进入人工扫码/更新 Cookie
 */
@Slf4j
@Service
public class CaptchaService {
    public static final String AUTO_SLIDER_FAILED_MESSAGE = "自动滑块失败，请人工更新 Cookie";

    @Autowired
    private BrowserPool browserPool;

    @Autowired
    private XianyuSliderStealthService xianyuSliderStealthService;

    @Autowired
    private CaptchaUrlProbeService captchaUrlProbeService;

    @Autowired(required = false)
    private PasswordLoginService passwordLoginService;

    /**
     * 验证结果
     */
    @Data
    public static class CaptchaResult {
        private boolean needCaptcha;        // 是否需要验证
        private boolean autoVerifySuccess;  // 自动验证是否成功
        private String message;             // 提示信息
        private String cookieText;           // 自动验证成功后导出的Cookie

        public static CaptchaResult noCaptcha() {
            CaptchaResult result = new CaptchaResult();
            result.setNeedCaptcha(false);
            result.setMessage("无需验证");
            return result;
        }

        public static CaptchaResult autoSuccess(String cookieText) {
            CaptchaResult result = new CaptchaResult();
            result.setNeedCaptcha(true);
            result.setAutoVerifySuccess(true);
            result.setMessage("自动验证成功");
            result.setCookieText(cookieText);
            return result;
        }

        public static CaptchaResult autoFailed() {
            CaptchaResult result = new CaptchaResult();
            result.setNeedCaptcha(true);
            result.setAutoVerifySuccess(false);
            result.setMessage(AUTO_SLIDER_FAILED_MESSAGE);
            return result;
        }

        public static CaptchaResult error(String message) {
            CaptchaResult result = new CaptchaResult();
            result.setMessage(message);
            return result;
        }
    }

    private record CaptchaBrowserRef(BrowserPool.BrowserInstance instance, boolean sharedBrowserContext) {
    }

    /**
     * 检测并处理滑块验证
     *
     * @param accountId 账号ID
     * @param cookieText Cookie字符串
     * @param targetUrl 目标URL
     * @param autoVerifyEnabled 是否启用自动验证
     * @return 验证结果
     */
    public CaptchaResult handleCaptcha(Long accountId, String cookieText,
                                       String targetUrl, boolean autoVerifyEnabled) {
        try {
            log.info("检测滑块验证: accountId={}, url={}", accountId, targetUrl);
            boolean knownCaptchaUrl = isKnownCaptchaUrl(targetUrl);
            if (knownCaptchaUrl) {
                log.info("目标URL已指向验证页: accountId={}, url={}", accountId, targetUrl);
                if (autoVerifyEnabled) {
                    return runFullSliderVerification(accountId, cookieText, targetUrl);
                }
            }

            // 获取浏览器实例
            CaptchaBrowserRef browserRef =
                openCaptchaBrowser(accountId, cookieText);
            BrowserPool.BrowserInstance browserInstance = browserRef == null ? null : browserRef.instance();

            if (browserInstance == null) {
                if (knownCaptchaUrl && autoVerifyEnabled) {
                    return runFullSliderVerification(accountId, cookieText, targetUrl);
                }
                return CaptchaResult.error("无法获取浏览器实例");
            }

            Page page = browserInstance.getPage();

            // 访问目标URL
            page.navigate(targetUrl);
            page.waitForTimeout(2000);

            // 检测是否存在滑块验证
            boolean hasCaptcha = knownCaptchaUrl || detectCaptcha(page);

            if (!hasCaptcha) {
                log.info("未检测到滑块验证: accountId={}", accountId);
                return CaptchaResult.noCaptcha();
            }

            log.warn("检测到滑块验证: accountId={}", accountId);

            // 尝试自动验证（可选）
            if (autoVerifyEnabled) {
                return runFullSliderVerification(accountId, cookieText, targetUrl);
            }

            return CaptchaResult.autoFailed();

        } catch (Exception e) {
            log.error("处理滑块验证失败: accountId={}", accountId, e);
            return CaptchaResult.error("处理验证失败: " + e.getMessage());
        }
    }

    public CaptchaResult handleRequiredCaptcha(Long accountId, String cookieText, String targetUrl) {
        // 主动探测最新的 verification_url
        String resolvedUrl = targetUrl;
        if (resolvedUrl == null || resolvedUrl.isBlank() || !isKnownCaptchaUrl(resolvedUrl)) {
            try {
                CaptchaUrlProbeService.ProbeResult probeResult = captchaUrlProbeService.probe(cookieText);
                if (probeResult != null && probeResult.needsVerification() && probeResult.getVerificationUrl() != null) {
                    resolvedUrl = probeResult.getVerificationUrl();
                    log.info("主动探测到最新验证URL: accountId={}, url={}", accountId, resolvedUrl);
                }
            } catch (Exception e) {
                log.debug("验证URL探测失败，使用原始URL: {}", e.getMessage());
            }
        }
        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            resolvedUrl = "https://www.goofish.com/im";
        }
        log.info("执行完整验证链路: accountId={}, url={}", accountId, resolvedUrl);
        return runFullSliderVerification(accountId, cookieText, resolvedUrl);
    }

    private boolean isKnownCaptchaUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return false;
        }
        String normalizedUrl = targetUrl.toLowerCase();
        return normalizedUrl.contains("punish")
            || normalizedUrl.contains("captcha")
            || normalizedUrl.contains("verify")
            || normalizedUrl.contains("x5secdata")
            || normalizedUrl.contains("x5step=2")
            || normalizedUrl.contains("action=captcha");
    }

    private CaptchaBrowserRef openCaptchaBrowser(Long accountId, String cookieText) {
        BrowserPool.BrowserInstance existingBrowser = browserPool.getExistingBrowser(accountId, true);
        if (existingBrowser != null) {
            return new CaptchaBrowserRef(existingBrowser, true);
        }
        BrowserPool.BrowserInstance createdBrowser = browserPool.getBrowser(accountId, cookieText, true, true);
        if (createdBrowser == null) {
            return null;
        }
        return new CaptchaBrowserRef(createdBrowser, false);
    }

    /**
     * 检测是否存在滑块验证
     */
    private boolean detectCaptcha(Page page) {
        try {
            // 等待页面加载完成
            page.waitForLoadState();
            page.waitForTimeout(1000);

            // 检查页面标题或URL是否包含验证相关关键词（优先检查，因为API返回的验证URL通常包含这些关键词）
            String title = page.title().toLowerCase();
            String url = page.url().toLowerCase();

            if (title.contains("验证") || title.contains("captcha") || title.contains("punish") ||
                url.contains("verify") || url.contains("captcha") || url.contains("punish")) {
                log.info("页面标题或URL包含验证关键词: title={}, url={}", title, url);
                return true;
            }

            // 常见的滑块验证元素选择器
            String[] captchaSelectors = {
                ".geetest_holder",           // 极验
                ".geetest_panel",
                ".geetest_slider",
                "#nc_1_wrapper",             // 阿里云滑块
                ".nc-container",
                ".nc_wrapper",
                ".captcha-container",        // 通用
                ".slider-container",
                "[class*='captcha']",
                "[class*='slider']",
                "[id*='captcha']",
                "[id*='nc_']",               // 阿里云滑块ID前缀
                "iframe[src*='captcha']",    // 验证码iframe
                "iframe[src*='verify']"
            };

            for (String selector : captchaSelectors) {
                try {
                    if (page.locator(selector).count() > 0) {
                        log.info("检测到滑块验证元素: {}", selector);
                        return true;
                    }
                } catch (Exception e) {
                    // 忽略单个选择器的错误
                }
            }

            // 检查页面内容是否包含验证相关文本
            String pageContent = page.content();
            if (pageContent.contains("滑动验证") || pageContent.contains("拖动滑块") ||
                pageContent.contains("请完成验证") || pageContent.contains("人机验证")) {
                log.info("页面内容包含验证相关文本");
                return true;
            }

            log.debug("未检测到滑块验证元素");
            return false;
        } catch (Exception e) {
            log.error("检测滑块验证失败", e);
            return false;
        }
    }

    private CaptchaResult runFullSliderVerification(Long accountId, String cookieText, String targetUrl) {
        // 优先尝试账密登录恢复（跳过自动滑块，滑块当前不稳定）
        if (passwordLoginService != null && accountId != null) {
            log.info("跳过自动滑块，直接尝试账密登录恢复: accountId={}", accountId);
            String newCookie = passwordLoginService.tryPasswordLogin(accountId);
            if (newCookie != null && !newCookie.isBlank()) {
                log.info("账密登录恢复成功: accountId={}, cookieLength={}", accountId, newCookie.length());
                return CaptchaResult.autoSuccess(newCookie);
            }
            log.warn("账密登录恢复失败: accountId={}", accountId);
        }

        // 账密登录失败后进入人工扫码/更新 Cookie 兜底
        log.warn("账密登录恢复失败，进入人工扫码/更新 Cookie 兜底: accountId={}", accountId);
        if (xianyuSliderStealthService != null) {
            XianyuSliderStealthService.SliderVerificationResult manualRecoveryResult =
                    xianyuSliderStealthService.verify(accountId, cookieText, targetUrl, true);
            if (manualRecoveryResult.isSuccess()
                    && manualRecoveryResult.getCookieText() != null
                    && !manualRecoveryResult.getCookieText().isBlank()) {
                log.info("人工扫码/验证恢复成功: accountId={}, cookieLength={}",
                        accountId, manualRecoveryResult.getCookieText().length());
                return CaptchaResult.autoSuccess(manualRecoveryResult.getCookieText());
            }
        }

        return CaptchaResult.autoFailed();
    }
}
