package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.HttpHeader;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class SellerSessionRefreshServiceImpl implements SellerSessionRefreshService {
    private static final String API_NAME = "mtop.taobao.idle.trade.merchant.sold.get";
    private static final String API_VERSION = "1.0";
    private static final String APP_KEY = "34839810";
    private static final String HOME_URL = "https://www.goofish.com/";
    private static final String SELLER_URL = "https://seller.goofish.com/";
    private static final String SELLER_ORIGIN = "https://seller.goofish.com";
    private static final String SELLER_REFERER =
            "https://seller.goofish.com/?site=COMMONPRO#/seller-trade/order-manage";
    private static final String SPM_CNT = "a21107h.42831410.0.0";
    private static final String SESSION_EXPIRED_CODE = "FAIL_SYS_SESSION_EXPIRED";
    private static final List<String> COOKIE_URLS = List.of(
            SELLER_URL,
            HOME_URL,
            "https://h5api.m.goofish.com",
            "https://passport.goofish.com",
            "https://www.taobao.com"
    );

    private final XianyuCookieMapper cookieMapper;
    private final AccountIdentityGuard accountIdentityGuard;
    private final CookieStateService cookieStateService;
    private final CaptchaCookieMergeService cookieMergeService;
    private final SliderBrowserFingerprintService fingerprintService;
    private final SliderCookieRefreshVerifier cookieRefreshVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SellerSessionRefreshServiceImpl(XianyuCookieMapper cookieMapper,
                                           AccountIdentityGuard accountIdentityGuard,
                                           CookieStateService cookieStateService,
                                           CaptchaCookieMergeService cookieMergeService,
                                           SliderBrowserFingerprintService fingerprintService,
                                           SliderCookieRefreshVerifier cookieRefreshVerifier) {
        this.cookieMapper = cookieMapper;
        this.accountIdentityGuard = accountIdentityGuard;
        this.cookieStateService = cookieStateService;
        this.cookieMergeService = cookieMergeService;
        this.fingerprintService = fingerprintService;
        this.cookieRefreshVerifier = cookieRefreshVerifier;
    }

    @Override
    public CookieRecoveryResult refreshSellerSession(Long accountId, String cookieText) {
        String baseCookie = resolveBaseCookie(accountId, cookieText);
        if (accountId == null || isBlank(baseCookie)) {
            return CookieRecoveryResult.failed("卖家订单接口 Session 激活失败：缺少账号或Cookie");
        }
        if (!accountIdentityGuard.canUseCookie(accountId, baseCookie)) {
            return CookieRecoveryResult.failed("卖家订单接口 Session 激活失败：Cookie身份不匹配");
        }

        Map<String, String> observedSetCookies = new LinkedHashMap<>();
        SliderBrowserFingerprintService.BrowserProfile profile = fingerprintService.profile(accountId);
        try (Playwright playwright = Playwright.create();
             Browser browser = launchBrowser(playwright, profile);
             BrowserContext context = newContext(browser, profile)) {
            context.addInitScript(fingerprintService.stealthScript(profile));
            context.addCookies(buildSeedCookies(baseCookie));
            Page page = context.newPage();
            fingerprintService.applyNetworkFingerprint(context, page, profile);
            Consumer<Response> listener = response -> collectSetCookieUpdates(response, observedSetCookies);
            context.onResponse(listener);
            try {
                SellerProbeResult probe = activateSellerSession(accountId, page, context, baseCookie, observedSetCookies);
                String mergedCookie = mergeCookieText(baseCookie, context, page, observedSetCookies);
                if (isBlank(mergedCookie) || !accountIdentityGuard.canUseCookie(accountId, mergedCookie)) {
                    return CookieRecoveryResult.failed("卖家订单接口 Session 激活失败：浏览器返回Cookie不可用");
                }
                boolean cookieChanged = hasCookieChanged(baseCookie, mergedCookie);
                if (!probe.success()) {
                    log.warn("【账号{}】卖家订单接口 Session 激活未通过探针，cookieChanged={}", accountId, cookieChanged);
                    return CookieRecoveryResult.failed(
                            "卖家订单接口 Session 激活后仍过期，不代表连接管理Cookie无效");
                }
                writeCookie(accountId, mergedCookie);
                log.info("【账号{}】卖家订单接口 Session 已完成浏览器激活，probeSuccess={}, cookieChanged={}",
                        accountId, probe.success(), cookieChanged);
                return CookieRecoveryResult.success(mergedCookie, "卖家订单接口 Session 已通过浏览器激活");
            } finally {
                context.offResponse(listener);
            }
        } catch (Exception e) {
            log.error("【账号{}】卖家订单接口 Session 浏览器激活失败", accountId, e);
            return CookieRecoveryResult.failed("卖家订单接口 Session 浏览器激活异常: " + e.getMessage());
        }
    }

    private SellerProbeResult activateSellerSession(Long accountId, Page page, BrowserContext context,
                                                    String baseCookie, Map<String, String> observedSetCookies) {
        navigate(page, HOME_URL);
        navigate(page, SELLER_REFERER);
        Map<String, String> cookies = cookieRefreshVerifier.snapshotCookies(context, page, observedSetCookies);
        if (cookies.isEmpty()) {
            cookies = cookieMergeService.parseCookieText(baseCookie);
        }
        Map<String, String> mergedProbeCookies = new LinkedHashMap<>(cookieMergeService.parseCookieText(baseCookie));
        mergedProbeCookies.putAll(cookies);
        mergedProbeCookies.putAll(observedSetCookies);
        String probeText = probeSellerOrderApi(page, mergedProbeCookies);
        boolean success = probeText.contains("SUCCESS");
        if (!success && !observedSetCookies.isEmpty()) {
            Map<String, String> retryCookies = new LinkedHashMap<>(mergedProbeCookies);
            retryCookies.putAll(observedSetCookies);
            String retryProbeText = probeSellerOrderApi(page, retryCookies);
            if (retryProbeText.contains("SUCCESS")) {
                return new SellerProbeResult(true, retryProbeText);
            }
            if (!retryProbeText.isBlank()) {
                probeText = retryProbeText;
            }
        }
        if (!success && probeText.contains(SESSION_EXPIRED_CODE)) {
            log.warn("【账号{}】浏览器内卖家订单接口预热仍返回 Session 过期: {}",
                    accountId, summarizeProbeText(probeText));
        } else if (!success) {
            log.warn("【账号{}】浏览器内卖家订单接口预热未成功: {}", accountId, summarizeProbeText(probeText));
        }
        waitForSettle(page, 1200);
        return new SellerProbeResult(success, probeText);
    }

    private String probeSellerOrderApi(Page page, Map<String, String> cookies) {
        String token = XianyuSignUtils.extractToken(cookies);
        String userId = cookies.getOrDefault("unb", "").trim();
        if (isBlank(token) || isBlank(userId)) {
            return "";
        }
        try {
            String dataJson = objectMapper.writeValueAsString(buildPageData(userId));
            String url = buildUrl(token, dataJson);
            String body = "data=" + encode(dataJson);
            Object value = page.evaluate("""
                    async ({ url, body }) => {
                        const controller = new AbortController();
                        const timer = setTimeout(() => controller.abort(), 8000);
                        try {
                            const resp = await fetch(url, {
                                method: 'POST',
                                credentials: 'include',
                                cache: 'no-store',
                                headers: {
                                    'accept': 'application/json',
                                    'content-type': 'application/x-www-form-urlencoded',
                                    'idle_site_biz_code': 'COMMONPRO',
                                    'idle_user_group_member_id': ''
                                },
                                body,
                                signal: controller.signal
                            });
                            const text = await resp.text();
                            return { status: resp.status, ok: resp.ok, text: text.slice(0, 1200) };
                        } catch (error) {
                            return { status: 0, ok: false, text: String((error && error.message) || error || '') };
                        } finally {
                            clearTimeout(timer);
                        }
                    }
                    """, Map.of("url", url, "body", body));
            if (value instanceof Map<?, ?> map) {
                Object text = map.containsKey("text") ? map.get("text") : "";
                return String.valueOf(text);
            }
        } catch (Exception e) {
            log.warn("卖家订单接口浏览器预热失败: {}", e.getMessage());
        }
        return "";
    }

    private Browser launchBrowser(Playwright playwright, SliderBrowserFingerprintService.BrowserProfile profile) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setIgnoreDefaultArgs(List.of("--enable-automation"))
                .setArgs(buildLaunchArgs(profile));
        PlaywrightBrowserUtils.resolveChromiumPath().ifPresent(options::setExecutablePath);
        return playwright.chromium().launch(options);
    }

    private BrowserContext newContext(Browser browser, SliderBrowserFingerprintService.BrowserProfile profile) {
        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(profile.getViewportWidth(), profile.getViewportHeight())
                .setScreenSize(profile.getViewportWidth(), profile.getViewportHeight())
                .setDeviceScaleFactor(profile.getDeviceScaleFactor())
                .setIsMobile(false)
                .setHasTouch(false)
                .setUserAgent(profile.getUserAgent())
                .setLocale(profile.getLocale())
                .setTimezoneId(profile.getTimezoneId())
                .setExtraHTTPHeaders(fingerprintService.extraHeaders(profile)));
    }

    private List<String> buildLaunchArgs(SliderBrowserFingerprintService.BrowserProfile profile) {
        return List.of(
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--disable-dev-shm-usage",
                "--no-first-run",
                "--disable-blink-features=AutomationControlled",
                "--window-size=" + profile.getViewportWidth() + "," + profile.getViewportHeight(),
                "--lang=" + profile.getLocale(),
                "--accept-lang=" + profile.getAcceptLanguage(),
                "--mute-audio",
                "--no-default-browser-check",
                "--force-color-profile=srgb",
                "--password-store=basic",
                "--use-mock-keychain",
                "--fingerprint=" + profile.getFingerprintSeed(),
                "--fingerprint-platform=windows",
                "--fingerprint-platform-version=10.0.0",
                "--fingerprint-brand=Chrome",
                "--fingerprint-brand-version=" + profile.getMajorVersion(),
                "--fingerprint-hardware-concurrency=" + profile.getHardwareConcurrency(),
                "--timezone=" + profile.getTimezoneId()
        );
    }

    private List<Cookie> buildSeedCookies(String cookieText) {
        List<Cookie> cookies = new ArrayList<>();
        cookieMergeService.parseCookieText(cookieText).forEach((name, value) -> {
            cookies.add(new Cookie(name, value).setDomain(".goofish.com").setPath("/"));
            cookies.add(new Cookie(name, value).setDomain(".taobao.com").setPath("/"));
        });
        return cookies;
    }

    private String mergeCookieText(String baseCookie, BrowserContext context, Page page,
                                   Map<String, String> observedSetCookies) {
        Map<String, String> browserCookies = new LinkedHashMap<>(
                cookieRefreshVerifier.snapshotCookies(context, page, observedSetCookies));
        for (com.microsoft.playwright.options.Cookie cookie : context.cookies(COOKIE_URLS)) {
            if (!isBlank(cookie.name) && !isBlank(cookie.value)) {
                browserCookies.put(cookie.name, cookie.value);
            }
        }
        CaptchaCookieMergeService.CookieMergeResult result =
                cookieMergeService.merge(baseCookie, browserCookies, observedSetCookies);
        return cookieMergeService.formatCookieText(result.getMergedCookies());
    }

    private void writeCookie(Long accountId, String cookieText) {
        Map<String, String> cookies = cookieMergeService.parseCookieText(cookieText);
        String mh5tk = cookies.get("_m_h5_tk");
        XianyuCookie latestCookie = cookieStateService.latestCookie(accountId);
        LambdaUpdateWrapper<XianyuCookie> wrapper = new LambdaUpdateWrapper<XianyuCookie>()
                .eq(latestCookie != null && latestCookie.getId() != null,
                        XianyuCookie::getId, latestCookie == null ? null : latestCookie.getId())
                .eq(latestCookie == null || latestCookie.getId() == null,
                        XianyuCookie::getXianyuAccountId, accountId)
                .set(XianyuCookie::getCookieText, cookieText)
                .set(XianyuCookie::getUpdatedTime, DateTimeUtils.currentShanghaiTimeWithMillis())
                .set(!isBlank(mh5tk), XianyuCookie::getMH5Tk, mh5tk);
        cookieMapper.update(null, wrapper);
        cookieStateService.markValid(accountId);
    }

    private Map<String, Object> buildPageData(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("needGroupInfo", Boolean.TRUE);
        data.put("pageNumber", 1);
        data.put("pageSize", 20);
        data.put("lastEndRow", "0");
        data.put("userId", userId);
        return data;
    }

    private String buildUrl(String token, String dataJson) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", APP_KEY);
        params.put("t", timestamp);
        params.put("sign", XianyuSignUtils.generateSign(timestamp, token, dataJson));
        params.put("v", API_VERSION);
        params.put("type", "json");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", API_NAME);
        params.put("valueType", "string");
        params.put("sessionOption", "AutoLoginOnly");
        params.put("spm_cnt", SPM_CNT);
        StringBuilder url = new StringBuilder("https://h5api.m.goofish.com/h5/");
        url.append(API_NAME).append("/").append(API_VERSION).append("/?");
        params.forEach((key, value) -> url.append(encode(key)).append("=").append(encode(value)).append("&"));
        return url.substring(0, url.length() - 1);
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
            updates.putAll(cookieMergeService.extractSetCookieUpdates(headers));
        } catch (Exception e) {
            log.debug("捕获卖家域 Set-Cookie 失败: {}", e.getMessage());
        }
    }

    private void navigate(Page page, String url) {
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(20_000));
            waitForSettle(page, 1000);
        } catch (Exception e) {
            log.warn("卖家Session激活导航失败: url={}, error={}", url, e.getMessage());
        }
    }

    private void waitForSettle(Page page, int timeoutMs) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(5000));
        } catch (Exception ignored) {
        }
        page.waitForTimeout(timeoutMs);
    }

    private String resolveBaseCookie(Long accountId, String cookieText) {
        if (!isBlank(cookieText)) {
            return cookieText;
        }
        XianyuCookie latest = cookieStateService.latestCookie(accountId);
        return latest == null ? null : latest.getCookieText();
    }

    private boolean hasCookieChanged(String oldCookie, String newCookie) {
        return !cookieMergeService.parseCookieText(oldCookie).equals(cookieMergeService.parseCookieText(newCookie));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String summarizeProbeText(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 300 ? compact : compact.substring(0, 300);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SellerProbeResult(boolean success, String text) {
    }
}
