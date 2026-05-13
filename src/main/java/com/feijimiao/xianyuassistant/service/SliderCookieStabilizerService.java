package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.HttpHeader;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class SliderCookieStabilizerService {
    private static final String HOME_URL = "https://www.goofish.com/";
    private static final String IM_URL = "https://www.goofish.com/im";
    private static final String TOKEN_API =
            "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/";
    private static final String USER_API =
            "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.loginuser.get/1.0/";
    private static final List<String> PROTECTED_FIELDS = List.of(
            "unb",
            "sgcookie",
            "cookie2",
            "_m_h5_tk",
            "_m_h5_tk_enc",
            "t",
            "cna",
            "havana_lgc2_77",
            "_tb_token_"
    );

    private final SliderCookieRefreshVerifier cookieRefreshVerifier;
    private final CaptchaCookieMergeService captchaCookieMergeService;

    public SliderCookieStabilizerService(SliderCookieRefreshVerifier cookieRefreshVerifier,
                                         CaptchaCookieMergeService captchaCookieMergeService) {
        this.cookieRefreshVerifier = cookieRefreshVerifier;
        this.captchaCookieMergeService = captchaCookieMergeService;
    }

    Map<String, String> stabilize(BrowserContext context,
                                  Page page,
                                  Map<String, String> initialCookies,
                                  Map<String, String> responseCookieUpdates) {
        Map<String, String> best = new LinkedHashMap<>(
                initialCookies == null ? Map.of() : initialCookies
        );
        best.putAll(cookieRefreshVerifier.snapshotCookies(context, page, responseCookieUpdates));
        List<String> bestMissing = missingProtected(best);
        logCookieIntegrity(best, "滑块成功后初始快照");
        if (bestMissing.isEmpty()) {
            return best;
        }
        Page workPage = resolveWorkPage(context, page);
        if (workPage == null) {
            return best;
        }
        log.info("滑块成功后检测到关键 Cookie 缺失，开始 reload/导航稳定化: missing={}", bestMissing);
        for (StabilizeAction action : stabilizeActions()) {
            Map<String, String> current = runStabilizeAction(context, workPage, action, responseCookieUpdates);
            if (!current.isEmpty() && missingProtected(current).size() < bestMissing.size()) {
                best = current;
                bestMissing = missingProtected(best);
                log.info("滑块成功后稳定化 Cookie 缺失减少: action={}, missing={}", action.getName(), bestMissing);
            }
            if (bestMissing.isEmpty()) {
                return best;
            }
        }
        Map<String, String> warmed = runMtopProbes(context, workPage, best, responseCookieUpdates);
        if (!warmed.isEmpty() && missingProtected(warmed).size() < bestMissing.size()) {
            return warmed;
        }
        return best;
    }

    private List<StabilizeAction> stabilizeActions() {
        return List.of(
                new StabilizeAction("reload_current", null),
                new StabilizeAction("goto_home", HOME_URL),
                new StabilizeAction("goto_im", IM_URL)
        );
    }

    private Map<String, String> runStabilizeAction(BrowserContext context,
                                                   Page page,
                                                   StabilizeAction action,
                                                   Map<String, String> responseCookieUpdates) {
        try {
            if (action.getUrl() == null) {
                log.info("滑块成功后稳定化动作: {}", action.getName());
                page.reload(new Page.ReloadOptions().setTimeout(15000));
            } else {
                log.info("滑块成功后稳定化动作: {} -> {}", action.getName(), action.getUrl());
                page.navigate(action.getUrl(), new Page.NavigateOptions().setTimeout(15000));
            }
            waitForSettle(page, 1000, 8000, 500);
        } catch (Exception e) {
            log.warn("滑块成功后稳定化动作失败: action={}, error={}", action.getName(), e.getMessage());
            return Map.of();
        }
        Map<String, String> current = cookieRefreshVerifier.snapshotCookies(context, page, responseCookieUpdates);
        logCookieIntegrity(current, "滑块成功后稳定化[" + action.getName() + "]");
        return current;
    }

    private Map<String, String> runMtopProbes(BrowserContext context,
                                              Page page,
                                              Map<String, String> initialCookies,
                                              Map<String, String> responseCookieUpdates) {
        Map<String, String> best = new LinkedHashMap<>(initialCookies == null ? Map.of() : initialCookies);
        List<MtopProbe> probes = buildMtopProbes(best);
        if (probes.isEmpty()) {
            log.info("滑块成功后业务预热跳过：缺少 _m_h5_tk 或 unb");
            return best;
        }
        log.info("滑块成功后标准稳定化仍缺 Cookie，开始浏览器 mtop 业务预热: missing={}", missingProtected(best));
        for (MtopProbe probe : probes) {
            ProbeResult result = executeProbe(context, page, probe);
            if (!result.getSetCookieUpdates().isEmpty()) {
                responseCookieUpdates.putAll(result.getSetCookieUpdates());
                log.info("滑块成功后业务预热响应补充 Cookie: probe={}, fields={}",
                        probe.getName(), result.getSetCookieUpdates().keySet());
            }
            if (result.getText().contains("FAIL_SYS_USER_VALIDATE")) {
                log.warn("滑块成功后业务预热仍返回验证提示: probe={}, summary={}",
                        probe.getName(), result.getText());
            }
            waitForSettle(page, 1000, 5000, 500);
            Map<String, String> current = cookieRefreshVerifier.snapshotCookies(context, page, responseCookieUpdates);
            logCookieIntegrity(current, "滑块成功后业务预热[" + probe.getName() + "]");
            if (!current.isEmpty() && missingProtected(current).size() < missingProtected(best).size()) {
                best = current;
                log.info("滑块成功后业务预热 Cookie 缺失减少: probe={}, missing={}",
                        probe.getName(), missingProtected(best));
            }
            if (missingProtected(best).isEmpty()) {
                break;
            }
        }
        return best;
    }

    private ProbeResult executeProbe(BrowserContext context, Page page, MtopProbe probe) {
        try {
            if (context != null && context.request() != null) {
                RequestOptions options = RequestOptions.create()
                        .setData(probe.getBody())
                        .setTimeout(5000)
                        .setHeader("accept", "application/json, text/plain, */*")
                        .setHeader("content-type", "application/x-www-form-urlencoded");
                APIResponse response = context.request().post(probe.getUrl(), options);
                String text = safeText(response);
                return new ProbeResult(
                        response.status(),
                        response.ok(),
                        text.length() > 600 ? text.substring(0, 600) : text,
                        extractSetCookieUpdates(response)
                );
            }
        } catch (Exception e) {
            log.debug("滑块成功后业务预热 request.post 失败，回退页面 fetch: {}", e.getMessage());
        }
        return executeProbeByFetch(page, probe);
    }

    private ProbeResult executeProbeByFetch(Page page, MtopProbe probe) {
        if (page == null || page.isClosed()) {
            return new ProbeResult(0, false, "", Map.of());
        }
        try {
            Object value = page.evaluate("""
                    async ({ url, body }) => {
                        const controller = new AbortController();
                        const timer = setTimeout(() => controller.abort(), 5000);
                        try {
                            const resp = await fetch(url, {
                                method: 'POST',
                                credentials: 'include',
                                cache: 'no-store',
                                headers: {
                                    'accept': 'application/json, text/plain, */*',
                                    'content-type': 'application/x-www-form-urlencoded'
                                },
                                body,
                                signal: controller.signal
                            });
                            const text = await resp.text();
                            return { status: resp.status, ok: resp.ok, text: text.slice(0, 600) };
                        } catch (error) {
                            return { status: 0, ok: false, text: String((error && error.message) || error || '') };
                        } finally {
                            clearTimeout(timer);
                        }
                    }
                    """, Map.of("url", probe.getUrl(), "body", probe.getBody()));
            if (value instanceof Map<?, ?> map) {
                return new ProbeResult(
                        toInt(map.get("status")),
                        Boolean.TRUE.equals(map.get("ok")),
                        String.valueOf(map.containsKey("text") ? map.get("text") : ""),
                        Map.of()
                );
            }
        } catch (Exception e) {
            log.warn("滑块成功后业务预热 fetch 失败: probe={}, error={}", probe.getName(), e.getMessage());
        }
        return new ProbeResult(0, false, "", Map.of());
    }

    private List<MtopProbe> buildMtopProbes(Map<String, String> cookies) {
        String token = XianyuSignUtils.extractToken(cookies);
        String userId = cookies.getOrDefault("unb", "").trim();
        if (token.isEmpty() || userId.isEmpty()) {
            return List.of();
        }
        List<MtopProbe> probes = new ArrayList<>();
        String tokenData = "{\"appKey\":\"444e9908a51d1cb236a27862abc769c9\",\"deviceId\":\""
                + generateDeviceId(userId) + "\"}";
        probes.add(new MtopProbe("login_token_fetch", buildMtopUrl(TOKEN_API,
                "mtop.taobao.idlemessage.pc.login.token", token, tokenData, true),
                "data=" + encode(tokenData)));
        String userData = "{}";
        probes.add(new MtopProbe("login_user_fetch", buildMtopUrl(USER_API,
                "mtop.taobao.idlemessage.pc.loginuser.get", token, userData, false),
                "data=" + encode(userData)));
        return probes;
    }

    private String buildMtopUrl(String baseUrl, String api, String token, String data, boolean tokenProbe) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", "34839810");
        params.put("t", timestamp);
        params.put("sign", XianyuSignUtils.generateSign(timestamp, token, data));
        params.put("v", "1.0");
        params.put("type", "originaljson");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", api);
        params.put("sessionOption", "AutoLoginOnly");
        params.put("spm_cnt", "a21ybx.im.0.0");
        if (tokenProbe) {
            params.put("dangerouslySetWindvaneParams", "[object Object]");
            params.put("smToken", "token");
            params.put("queryToken", "sm");
            params.put("sm", "sm");
        }
        StringBuilder builder = new StringBuilder(baseUrl).append("?");
        params.forEach((key, value) -> builder.append(encode(key)).append("=").append(encode(value)).append("&"));
        return builder.substring(0, builder.length() - 1);
    }

    private String generateDeviceId(String userId) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder builder = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 36; i++) {
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                builder.append("-");
            } else if (i == 14) {
                builder.append("4");
            } else {
                int value = random.nextInt(16);
                builder.append(chars.charAt(i == 19 ? ((value & 0x3) | 0x8) : value));
            }
        }
        return builder.append("-").append(userId).toString();
    }

    private void waitForSettle(Page page, int firstWaitMs, int networkIdleMs, int lastWaitMs) {
        if (page == null || page.isClosed()) {
            return;
        }
        page.waitForTimeout(firstWaitMs);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(networkIdleMs));
        } catch (Exception ignored) {
        }
        page.waitForTimeout(lastWaitMs);
    }

    private Page resolveWorkPage(BrowserContext context, Page page) {
        if (page != null && !page.isClosed()) {
            return page;
        }
        if (context == null) {
            return null;
        }
        for (Page candidate : context.pages()) {
            if (candidate != null && !candidate.isClosed()) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, String> extractSetCookieUpdates(APIResponse response) {
        if (response == null) {
            return Map.of();
        }
        List<String> values = new ArrayList<>();
        try {
            for (HttpHeader header : response.headersArray()) {
                if ("set-cookie".equalsIgnoreCase(header.name)) {
                    values.add(header.value);
                }
            }
        } catch (Exception ignored) {
        }
        return captchaCookieMergeService.extractSetCookieUpdates(values);
    }

    private void logCookieIntegrity(Map<String, String> cookies, String scene) {
        List<String> missing = missingProtected(cookies);
        if (missing.isEmpty()) {
            log.info("{} Cookie 快照完整: fieldCount={}", scene, cookies == null ? 0 : cookies.size());
        } else {
            log.warn("{} Cookie 快照缺失关键字段: fieldCount={}, missing={}",
                    scene, cookies == null ? 0 : cookies.size(), missing);
        }
    }

    private List<String> missingProtected(Map<String, String> cookies) {
        List<String> missing = new ArrayList<>();
        for (String field : PROTECTED_FIELDS) {
            if (cookies == null || !hasText(cookies.get(field))) {
                missing.add(field);
            }
        }
        return missing;
    }

    private String safeText(APIResponse response) {
        try {
            return response.text();
        } catch (Exception e) {
            return "";
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Value
    private static class StabilizeAction {
        String name;
        String url;
    }

    @Value
    private static class MtopProbe {
        String name;
        String url;
        String body;
    }

    @Value
    private static class ProbeResult {
        int status;
        boolean ok;
        String text;
        Map<String, String> setCookieUpdates;
    }
}
