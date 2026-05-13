package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.exception.CookieExpiredException;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.CaptchaService;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTokenServiceImplTest {

    @Test
    void refreshTokenKeepsOldTokenUntilNewTokenIsFetched() {
        WebSocketTokenServiceImpl service = spy(new WebSocketTokenServiceImpl());
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);

        String token = service.refreshToken(55L);

        assertNull(token);
        verify(service, never()).clearToken(55L);
    }

    @Test
    void notifyCaptchaRequiredIfNeededDebouncesRepeatedNotifications() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        NotificationService notificationService = mock(NotificationService.class);
        NotificationContentBuilder contentBuilder = mock(NotificationContentBuilder.class);

        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "notificationContentBuilder", contentBuilder);

        when(contentBuilder.eventContent(eq(11L), anyString(), anyString(), anyString()))
                .thenReturn("content");

        ReflectionTestUtils.invokeMethod(service, "notifyCaptchaRequiredIfNeeded", 11L, "reason", "detail");
        ReflectionTestUtils.invokeMethod(service, "notifyCaptchaRequiredIfNeeded", 11L, "reason", "detail");

        verify(notificationService, times(1)).notifyEvent(
                eq(NotificationService.EVENT_CAPTCHA_REQUIRED),
                eq("【闲鱼助手】账号需要人机验证"),
                eq("content")
        );
    }

    @Test
    void handleCaptchaOrRequireCookieUpdateWritesCookieWhenAutoVerifySucceeds() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        CaptchaService captchaService = mock(CaptchaService.class);
        AccountService accountService = mock(AccountService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationContentBuilder contentBuilder = mock(NotificationContentBuilder.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        ReflectionTestUtils.setField(service, "captchaService", captchaService);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "notificationContentBuilder", contentBuilder);
        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("old-cookie");
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(captchaService.handleRequiredCaptcha(
                eq(11L),
                eq("old-cookie"),
                eq("https://www.goofish.com/im")))
                .thenReturn(CaptchaService.CaptchaResult.autoSuccess("unb=abc; new-cookie=value"));
        when(accountService.updateAccountCookie(11L, "abc", "unb=abc; new-cookie=value")).thenReturn(true);
        when(contentBuilder.eventContent(eq(11L), anyString(), anyString(), anyString()))
                .thenReturn("success-content");

        Boolean autoSolved = ReflectionTestUtils.invokeMethod(
                service, "handleCaptchaOrRequireCookieUpdate", 11L, "old-cookie", null);

        assertTrue(autoSolved);
        verify(accountService).updateAccountCookie(11L, "abc", "unb=abc; new-cookie=value");
        verify(notificationService).notifyEvent(
                eq(NotificationService.EVENT_CAPTCHA_SUCCESS),
                eq("【闲鱼助手】人机验证恢复成功"),
                eq("success-content"));
    }

    @Test
    void handleCaptchaOrRequireCookieUpdateUsesExtractedCaptchaUrl() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        CaptchaService captchaService = mock(CaptchaService.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        String captchaUrl = "https://h5api.m.goofish.com/h5/mtop.xxx/punish?x5secdata=abc";

        ReflectionTestUtils.setField(service, "captchaService", captchaService);
        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("old-cookie");
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(captchaService.handleRequiredCaptcha(
                eq(12L),
                eq("old-cookie"),
                eq(captchaUrl)))
                .thenReturn(CaptchaService.CaptchaResult.autoFailed());

        Boolean autoSolved = ReflectionTestUtils.invokeMethod(
                service, "handleCaptchaOrRequireCookieUpdate", 12L, "old-cookie", captchaUrl);

        assertFalse(autoSolved);
    }

    @Test
    void handleCaptchaOrRequireCookieUpdateBypassesBrowserPoolDetectionWhenCaptchaIsRequired() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        CaptchaService captchaService = mock(CaptchaService.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        ReflectionTestUtils.setField(service, "captchaService", captchaService);
        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("old-cookie");
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(captchaService.handleRequiredCaptcha(13L, "old-cookie", "https://www.goofish.com/im"))
                .thenReturn(CaptchaService.CaptchaResult.autoFailed());

        Boolean autoSolved = ReflectionTestUtils.invokeMethod(
                service, "handleCaptchaOrRequireCookieUpdate", 13L, "old-cookie", null);

        assertFalse(autoSolved);
        verify(captchaService).handleRequiredCaptcha(13L, "old-cookie", "https://www.goofish.com/im");
    }

    @Test
    void retryAutoCaptchaMarksWaitingWhenAutoVerifyFails() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        CaptchaService captchaService = mock(CaptchaService.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        ReflectionTestUtils.setField(service, "captchaService", captchaService);
        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("old-cookie");
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(captchaService.handleRequiredCaptcha(
                eq(22L),
                eq("old-cookie"),
                eq("https://www.goofish.com/im")))
                .thenReturn(CaptchaService.CaptchaResult.autoFailed());

        boolean result = service.retryAutoCaptcha(22L);

        assertFalse(result);
        assertTrue(service.isCaptchaWaiting(22L));
    }

    @Test
    void retryAutoCaptchaClearsWaitingWhenAutoVerifySucceeds() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        CaptchaService captchaService = mock(CaptchaService.class);
        AccountService accountService = mock(AccountService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationContentBuilder contentBuilder = mock(NotificationContentBuilder.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        ReflectionTestUtils.setField(service, "captchaService", captchaService);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "notificationService", notificationService);
        ReflectionTestUtils.setField(service, "notificationContentBuilder", contentBuilder);
        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("old-cookie");
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(captchaService.handleRequiredCaptcha(
                eq(33L),
                eq("old-cookie"),
                eq("https://www.goofish.com/im")))
                .thenReturn(CaptchaService.CaptchaResult.autoSuccess("unb=abc; new-cookie=value"));
        when(accountService.updateAccountCookie(33L, "abc", "unb=abc; new-cookie=value")).thenReturn(true);
        when(contentBuilder.eventContent(eq(33L), anyString(), anyString(), anyString()))
                .thenReturn("success-content");

        boolean result = service.retryAutoCaptcha(33L);

        assertTrue(result);
        assertFalse(service.isCaptchaWaiting(33L));
        verify(accountService).updateAccountCookie(33L, "abc", "unb=abc; new-cookie=value");
    }

    @Test
    void extractCaptchaUrlReturnsResponseDataUrl() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        String captchaUrl = "https://h5api.m.goofish.com/h5/mtop.xxx/punish?x5secdata=abc";

        String extracted = ReflectionTestUtils.invokeMethod(
                service,
                "extractCaptchaUrl",
                Map.of("data", Map.of("url", captchaUrl))
        );

        assertEquals(captchaUrl, extracted);
    }

    @Test
    void postSliderRetryDelayCanBeDisabledForUnitTests() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        ReflectionTestUtils.setField(service, "postSliderTokenRetryDelayMinMs", 0L);
        ReflectionTestUtils.setField(service, "postSliderTokenRetryDelayMaxMs", 0L);

        long startedAt = System.currentTimeMillis();
        ReflectionTestUtils.invokeMethod(service, "waitAfterSliderSuccess", 44L);

        assertTrue(System.currentTimeMillis() - startedAt < 200L);
    }

    @Test
    void handleTokenFailureBreaksLoopWhenPasswordLoginAlreadyUsedAndSessionStillExpired() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        OperationLogService operationLogService = mock(OperationLogService.class);

        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "operationLogService", operationLogService);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("any-cookie");
        cookie.setCookieStatus(1);
        when(cookieMapper.selectOne(any())).thenReturn(cookie);

        String sessionExpiredResp = "{\"ret\":[\"FAIL_SYS_SESSION_EXPIRED::Session过期\"]}";

        // passwordLoginUsed=true 时再次 Session 过期：必须抛 CookieExpiredException，避免无限循环
        assertThrows(CookieExpiredException.class, () -> ReflectionTestUtils.invokeMethod(
                service,
                "handleTokenFailure",
                77L,
                0,
                sessionExpiredResp,
                "Token API调用失败",
                false,
                0,
                true
        ));
    }

    @Test
    void handleTokenFailureFallbacksToPasswordLoginAfterHasLoginAttemptsExceedLimit() {
        WebSocketTokenServiceImpl service = spy(new WebSocketTokenServiceImpl());
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        CaptchaService captchaService = mock(CaptchaService.class);

        ReflectionTestUtils.setField(service, "xianyuCookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(service, "captchaService", captchaService);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("stale-cookie");
        cookie.setCookieStatus(1);
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        // 模拟账密登录路径失败，验证流程会抛 CookieExpiredException 并标记过期
        when(captchaService.handleRequiredCaptcha(eq(88L), eq("stale-cookie"), anyString()))
                .thenReturn(CaptchaService.CaptchaResult.autoFailed());

        String sessionExpiredResp = "{\"ret\":[\"FAIL_SYS_SESSION_EXPIRED::Session过期\"]}";

        // hasLoginAttempts=2 已达上限，必须转账密登录路径，账密失败后抛 CookieExpiredException
        assertThrows(CookieExpiredException.class, () -> ReflectionTestUtils.invokeMethod(
                service,
                "handleTokenFailure",
                88L,
                0,
                sessionExpiredResp,
                "Token API调用失败",
                false,
                2,
                false
        ));
        verify(captchaService, times(1)).handleRequiredCaptcha(eq(88L), eq("stale-cookie"), anyString());
    }
}
