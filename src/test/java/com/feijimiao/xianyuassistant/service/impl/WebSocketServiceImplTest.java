package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.CookieRefreshService;
import com.feijimiao.xianyuassistant.service.WebSocketTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketServiceImplTest {

    @Test
    void startWebSocketRefreshesCookieBeforeFailingWhenNoValidCookieIsFound() throws Exception {
        WebSocketServiceImpl service = spy(new WebSocketServiceImpl());
        AccountService accountService = mock(AccountService.class);
        CookieRefreshService cookieRefreshService = mock(CookieRefreshService.class);
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);
        String refreshedCookie = "unb=2219250854984; _m_h5_tk=token_1778020000000; cookie2=abc";

        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "cookieRefreshService", cookieRefreshService);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);

        when(accountService.getCookieByAccountId(2L)).thenReturn(null, refreshedCookie);
        when(cookieRefreshService.refreshCookie(2L)).thenReturn(true);
        when(accountService.getOrGenerateDeviceId(2L, "2219250854984")).thenReturn("device-id");
        when(tokenService.getAccessToken(2L)).thenReturn("access-token");
        doReturn(true).when(service)
            .connectWebSocket(2L, refreshedCookie, "device-id", "access-token", "2219250854984");

        assertTrue(service.startWebSocket(2L));

        verify(cookieRefreshService).refreshCookie(2L);
    }

    @Test
    void startWebSocketWithManualTokenAlsoRefreshesCookieBeforeFailing() throws Exception {
        WebSocketServiceImpl service = spy(new WebSocketServiceImpl());
        AccountService accountService = mock(AccountService.class);
        CookieRefreshService cookieRefreshService = mock(CookieRefreshService.class);
        String refreshedCookie = "unb=2219250854984; _m_h5_tk=token_1778020000000; cookie2=abc";

        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "cookieRefreshService", cookieRefreshService);

        when(accountService.getCookieByAccountId(2L)).thenReturn(null, refreshedCookie);
        when(cookieRefreshService.refreshCookie(2L)).thenReturn(true);
        when(accountService.getOrGenerateDeviceId(2L, "2219250854984")).thenReturn("device-id");
        doReturn(true).when(service)
            .connectWebSocket(2L, refreshedCookie, "device-id", "manual-token", "2219250854984");

        assertTrue(service.startWebSocketWithToken(2L, "manual-token"));

        verify(cookieRefreshService).refreshCookie(2L);
    }

    @Test
    void refreshTokenAndReconnectReturnsImmediatelyWhenCaptchaIsPending() {
        WebSocketServiceImpl service = spy(new WebSocketServiceImpl());
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);
        CookieRefreshService cookieRefreshService = mock(CookieRefreshService.class);

        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "cookieRefreshService", cookieRefreshService);

        when(tokenService.isCaptchaWaiting(3L)).thenReturn(true);

        ReflectionTestUtils.invokeMethod(service, "refreshTokenAndReconnect", 3L);

        verify(tokenService).isCaptchaWaiting(3L);
        verifyNoInteractions(cookieRefreshService);
    }

    @Test
    void scheduleReconnectSkipsTaskCreationWhenCaptchaIsPending() {
        WebSocketServiceImpl service = spy(new WebSocketServiceImpl());
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);

        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        when(tokenService.isCaptchaWaiting(4L)).thenReturn(true);

        ReflectionTestUtils.invokeMethod(service, "scheduleReconnect", 4L, 5, false);

        verify(tokenService).isCaptchaWaiting(4L);
        Object reconnectTasks = ReflectionTestUtils.getField(service, "reconnectTasks");
        assertTrue(reconnectTasks instanceof java.util.Map<?, ?> map && map.isEmpty());
    }
}
