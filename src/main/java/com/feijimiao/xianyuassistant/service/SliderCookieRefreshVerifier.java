package com.feijimiao.xianyuassistant.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SliderCookieRefreshVerifier {
    private static final long DEFAULT_MAX_POLL_MS = 10_000L;
    private static final long DEFAULT_POLL_INTERVAL_MS = 500L;
    private static final String X5_COOKIE_PREFIX = "x5";
    private static final List<String> TRACK_URLS = List.of(
            "https://www.goofish.com",
            "https://www.goofish.com/im",
            "https://h5api.m.goofish.com",
            "https://passport.goofish.com",
            "https://www.taobao.com"
    );
    private static final List<String> KEY_COOKIE_NAMES = List.of(
            "_m_h5_tk",
            "_m_h5_tk_enc",
            "cookie2",
            "unb",
            "sgcookie",
            "uc1",
            "uc3",
            "uc4",
            "csg",
            "sn"
    );
    private static final List<String> REQUIRED_SESSION_COOKIE_FIELDS = List.of(
            "unb",
            "sgcookie",
            "cookie2",
            "_m_h5_tk",
            "_m_h5_tk_enc",
            "t"
    );
    private static final List<String> SLIDER_SELECTORS = List.of(
            "#nc_1_n1z",
            ".btn_slide",
            ".sm-btn",
            ".sm-btn-wrapper",
            ".nc_scale"
    );

    private final SliderPageInspector sliderPageInspector;
    private final long maxPollMs;
    private final long pollIntervalMs;

    @Autowired
    public SliderCookieRefreshVerifier(SliderPageInspector sliderPageInspector) {
        this(sliderPageInspector, DEFAULT_MAX_POLL_MS, DEFAULT_POLL_INTERVAL_MS);
    }

    SliderCookieRefreshVerifier(SliderPageInspector sliderPageInspector,
                                long maxPollMs,
                                long pollIntervalMs) {
        this.sliderPageInspector = sliderPageInspector;
        this.maxPollMs = maxPollMs;
        this.pollIntervalMs = pollIntervalMs;
    }

    CookieRefreshCheck verify(BrowserContext context,
                              Page page,
                              Map<String, String> baseline,
                              Map<String, String> responseCookieUpdates) {
        Map<String, String> base = baseline == null ? Map.of() : baseline;
        Map<String, String> current = snapshotCookies(context, page, responseCookieUpdates);
        if (base.isEmpty()) {
            return CookieRefreshCheck.accepted(current, null, false, "Cookie 基线为空，跳过刷新校验");
        }
        if (hasMeaningfulCookieRefresh(base, current)) {
            return CookieRefreshCheck.accepted(current, true, false, "关键 Cookie 已刷新");
        }
        waitPage(page, 1200L);
        long deadline = System.currentTimeMillis() + Math.max(0L, maxPollMs);
        while (System.currentTimeMillis() < deadline) {
            waitPage(page, Math.max(100L, pollIntervalMs));
            current = snapshotCookies(context, page, responseCookieUpdates);
            if (hasMeaningfulCookieRefresh(base, current)) {
                return CookieRefreshCheck.accepted(current, true, false, "关键 Cookie 已刷新");
            }
        }
        if (shouldAcceptSoftSuccess(page, current)) {
            return CookieRefreshCheck.accepted(current, false, true, "页面已脱离验证态，接受无 Cookie 变化软成功");
        }
        return CookieRefreshCheck.rejected(current, "页面显示验证通过，但关键 Cookie 未确认刷新");
    }

    Map<String, String> snapshotCookies(BrowserContext context,
                                        Map<String, String> responseCookieUpdates) {
        return snapshotCookies(context, null, responseCookieUpdates);
    }

    Map<String, String> snapshotCookies(BrowserContext context,
                                        Page page,
                                        Map<String, String> responseCookieUpdates) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (context != null) {
            try {
                context.cookies(TRACK_URLS).forEach(cookie -> {
                    if (cookie.name != null && !cookie.name.isBlank()
                            && cookie.value != null && !cookie.value.isBlank()) {
                        cookies.put(cookie.name, cookie.value);
                    }
                });
            } catch (Exception e) {
                log.debug("Playwright Cookie 快照失败: {}", e.getMessage());
            }
            Map<String, String> cdpCookies = snapshotCookiesViaCdp(context, page);
            if (!cdpCookies.isEmpty()) {
                List<String> extraKeys = cdpCookies.keySet().stream()
                        .filter(name -> !cookies.containsKey(name))
                        .toList();
                cookies.putAll(cdpCookies);
                if (!extraKeys.isEmpty()) {
                    log.info("CDP Cookie 快照补充字段: count={}, fields={}",
                            extraKeys.size(),
                            extraKeys.size() > 12 ? extraKeys.subList(0, 12) : extraKeys);
                }
            }
        }
        if (responseCookieUpdates != null) {
            responseCookieUpdates.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                    cookies.put(name, value);
                }
            });
        }
        return cookies;
    }

    private Map<String, String> snapshotCookiesViaCdp(BrowserContext context, Page page) {
        Page probePage = resolveProbePage(context, page);
        if (probePage == null) {
            return Map.of();
        }
        CDPSession session = null;
        try {
            session = context.newCDPSession(probePage);
            try {
                session.send("Network.enable");
            } catch (Exception ignored) {
            }
            JsonObject response = session.send("Network.getAllCookies");
            JsonArray rawCookies = response == null ? null : response.getAsJsonArray("cookies");
            if (rawCookies == null || rawCookies.isEmpty()) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (JsonElement element : rawCookies) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject cookie = element.getAsJsonObject();
                String name = jsonString(cookie, "name");
                String value = jsonString(cookie, "value");
                if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                    result.put(name, value);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("CDP Cookie 快照失败: {}", e.getMessage());
            return Map.of();
        } finally {
            if (session != null) {
                try {
                    session.detach();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Page resolveProbePage(BrowserContext context, Page page) {
        if (page != null && !page.isClosed()) {
            return page;
        }
        if (context == null) {
            return null;
        }
        try {
            for (Page candidate : context.pages()) {
                if (candidate != null && !candidate.isClosed()) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String jsonString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    boolean hasMeaningfulCookieRefresh(Map<String, String> baseline, Map<String, String> current) {
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String name = entry.getKey();
            if (name != null && name.toLowerCase().startsWith(X5_COOKIE_PREFIX)
                    && isNewOrChangedCookie(baseline, name, entry.getValue())) {
                log.info("Cookie 刷新检测: x5 系 Cookie '{}' 已变化", name);
                return true;
            }
        }
        for (String name : KEY_COOKIE_NAMES) {
            if (isNewOrChangedCookie(baseline, name, current.get(name))) {
                log.info("Cookie 刷新检测: 关键会话 Cookie '{}' 已变化", name);
                return true;
            }
        }
        return false;
    }

    boolean shouldAcceptSoftSuccess(Page page, Map<String, String> currentCookies) {
        if (page == null || page.isClosed() || !hasCompletedLoginCookies(currentCookies)) {
            return false;
        }
        SliderPageInspector.PageState state = SliderPageInspector.PageState.builder()
                .url(safeUrl(page))
                .title(safeTitle(page))
                .bodyText(safeBodyText(page))
                .visibleSlider(hasVisibleSlider(page))
                .failureSignal(false)
                .build();
        return !state.isVisibleSlider()
                && !sliderPageInspector.looksLikeVerificationUrl(state.getUrl())
                && !sliderPageInspector.looksLikeVerificationTitle(state.getTitle())
                && !sliderPageInspector.looksLikeVerificationText(state.getBodyText());
    }

    private boolean isNewOrChangedCookie(Map<String, String> baseline, String name, String value) {
        if (name == null || value == null || value.isBlank()) {
            return false;
        }
        String oldValue = baseline.get(name);
        return oldValue == null || !oldValue.equals(value);
    }

    private boolean hasCompletedLoginCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return false;
        }
        for (String field : REQUIRED_SESSION_COOKIE_FIELDS) {
            String value = cookies.get(field);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasVisibleSlider(Page page) {
        for (String selector : SLIDER_SELECTORS) {
            try {
                if (page.locator(selector).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String safeUrl(Page page) {
        try {
            String url = page.url();
            return url == null ? "" : url;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeTitle(Page page) {
        try {
            String title = page.title();
            return title == null ? "" : title;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeBodyText(Page page) {
        try {
            String text = page.innerText("body", new Page.InnerTextOptions().setTimeout(1500));
            return text == null ? "" : text;
        } catch (Exception e) {
            try {
                String content = page.content();
                return content == null ? "" : content;
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    private void waitPage(Page page, long timeoutMs) {
        if (page == null || timeoutMs <= 0L) {
            return;
        }
        try {
            page.waitForTimeout(timeoutMs);
        } catch (Exception ignored) {
        }
    }

    @Value
    static class CookieRefreshCheck {
        boolean accepted;
        Map<String, String> currentCookies;
        Boolean cookieRefreshConfirmed;
        boolean softSuccess;
        String message;

        static CookieRefreshCheck accepted(Map<String, String> currentCookies,
                                           Boolean cookieRefreshConfirmed,
                                           boolean softSuccess,
                                           String message) {
            return new CookieRefreshCheck(true, currentCookies, cookieRefreshConfirmed, softSuccess, message);
        }

        static CookieRefreshCheck rejected(Map<String, String> currentCookies, String message) {
            return new CookieRefreshCheck(false, currentCookies, false, false, message);
        }
    }
}
