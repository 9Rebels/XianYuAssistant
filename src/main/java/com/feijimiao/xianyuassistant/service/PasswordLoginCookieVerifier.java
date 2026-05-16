package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
class PasswordLoginCookieVerifier {
    private static final String LOGIN_TOKEN_URL =
            "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/"
                    + "?jsv=2.7.2&appKey=34839810&type=originaljson&dataType=json&v=1.0"
                    + "&api=mtop.taobao.idlemessage.pc.login.token&sessionOption=AutoLoginOnly";
    private static final long SERVER_SESSION_PROBE_INTERVAL_MS = 10_000L;

    private final PasswordLoginStateInspector stateInspector;
    private final Map<Long, ServerSessionProbeState> serverSessionProbeStates = new ConcurrentHashMap<>();

    PasswordLoginCookieVerifier(PasswordLoginStateInspector stateInspector) {
        this.stateInspector = stateInspector;
    }

    LoginCookieCheck check(Long accountId, Page page, BrowserContext context) {
        Map<String, String> cookieMap = collectCookieMap(context);
        if (!stateInspector.hasCompletedLoginCookies(cookieMap)) {
            log.debug("[PasswordLogin] Cookie字段未齐全: missing={}",
                    stateInspector.missingRequiredCookieFields(cookieMap));
            return LoginCookieCheck.failed("Cookie字段未齐全");
        }
        if (!isServerSessionReady(accountId, page)) {
            return LoginCookieCheck.failed("服务端Session未就绪");
        }
        return LoginCookieCheck.success(formatCookies(cookieMap));
    }

    private boolean isLoginUrlCleared(Page page) {
        String url = safeUrl(page).toLowerCase();
        return !url.contains("passport.goofish.com") && !url.contains("login");
    }

    private boolean isServerSessionReady(Long accountId, Page page) {
        Long key = accountId == null ? -1L : accountId;
        ServerSessionProbeState state = serverSessionProbeStates.computeIfAbsent(
                key, ignored -> new ServerSessionProbeState());
        long now = System.currentTimeMillis();
        if (now - state.lastProbeAt < SERVER_SESSION_PROBE_INTERVAL_MS) {
            return state.ready;
        }
        state.lastProbeAt = now;
        state.ready = false;
        if (page == null || page.isClosed()) {
            return false;
        }
        return probeServerSession(page, state);
    }

    private boolean probeServerSession(Page page, ServerSessionProbeState state) {
        Page verifyPage = null;
        try {
            verifyPage = page.context().newPage();
            verifyPage.navigate(LOGIN_TOKEN_URL, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(10000));
            String content = verifyPage.content();
            if (content == null || content.isBlank()) {
                return false;
            }
            state.ready = !content.contains("FAIL_SYS_SESSION_EXPIRED")
                    && !content.contains("FAIL_SYS_USER_VALIDATE")
                    && !content.contains("令牌过期")
                    && !content.contains("Session过期");
            return state.ready;
        } catch (Exception e) {
            log.debug("[PasswordLogin] login.token预检异常: {}", e.getMessage());
            return false;
        } finally {
            if (verifyPage != null && !verifyPage.isClosed()) {
                try {
                    verifyPage.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private Map<String, String> collectCookieMap(BrowserContext context) {
        Map<String, String> map = new LinkedHashMap<>();
        List<Cookie> cookies = context.cookies(List.of(
                "https://www.goofish.com", "https://h5api.m.goofish.com",
                "https://www.taobao.com", "https://login.taobao.com"));
        for (Cookie c : cookies) {
            map.put(c.name, c.value);
        }
        return map;
    }

    private String formatCookies(Map<String, String> cookieMap) {
        return cookieMap.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private String safeUrl(Page page) {
        try {
            return page == null ? "" : String.valueOf(page.url());
        } catch (Exception e) {
            return "";
        }
    }

    record LoginCookieCheck(boolean success, String cookieText, String message) {
        static LoginCookieCheck success(String cookieText) {
            return new LoginCookieCheck(true, cookieText, "ok");
        }

        static LoginCookieCheck failed(String message) {
            return new LoginCookieCheck(false, null, message);
        }
    }

    private static class ServerSessionProbeState {
        private long lastProbeAt;
        private boolean ready;
    }
}
