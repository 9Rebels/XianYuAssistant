package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.PasswordLoginService;
import com.feijimiao.xianyuassistant.service.WebSocketTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketControllerTest {

    @Test
    void retryAutoCaptchaReturnsSuccessWhenAutoVerifySucceeds() {
        WebSocketController controller = new WebSocketController();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);

        ReflectionTestUtils.setField(controller, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(controller, "operationLogService", operationLogService);
        when(applicationContext.getBean(WebSocketTokenService.class)).thenReturn(tokenService);
        when(tokenService.retryAutoCaptcha(12L)).thenReturn(true);

        WebSocketController.RetryAutoCaptchaReqDTO reqDTO = new WebSocketController.RetryAutoCaptchaReqDTO();
        reqDTO.setXianyuAccountId(12L);

        ResultObject<String> result = controller.retryAutoCaptcha(reqDTO);

        assertEquals(200, result.getCode());
        assertEquals("自动滑块成功，Cookie 已更新", result.getData());
        verify(tokenService).retryAutoCaptcha(12L);
        verify(operationLogService).log(
                eq(12L), eq("VERIFY"), eq("COOKIE"), eq("手动触发自动滑块成功"),
                eq(1), eq("COOKIE"), eq("12"),
                any(), any(), any(), any()
        );
    }

    @Test
    void retryAutoCaptchaReturnsFailureWhenAutoVerifyFails() {
        WebSocketController controller = new WebSocketController();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);

        ReflectionTestUtils.setField(controller, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(controller, "operationLogService", operationLogService);
        when(applicationContext.getBean(WebSocketTokenService.class)).thenReturn(tokenService);
        when(tokenService.retryAutoCaptcha(15L)).thenReturn(false);

        WebSocketController.RetryAutoCaptchaReqDTO reqDTO = new WebSocketController.RetryAutoCaptchaReqDTO();
        reqDTO.setXianyuAccountId(15L);

        ResultObject<String> result = controller.retryAutoCaptcha(reqDTO);

        assertEquals(500, result.getCode());
        assertNull(result.getData());
        assertEquals("自动滑块失败，请人工更新 Cookie", result.getMsg());
        verify(operationLogService).log(
                eq(15L), eq("VERIFY"), eq("COOKIE"), eq("手动触发自动滑块失败"),
                eq(0), eq("COOKIE"), eq("15"),
                any(), any(), eq("自动滑块失败，请人工更新 Cookie"), any()
        );
    }

    @Test
    void retryAutoCaptchaRejectsMissingAccountId() {
        WebSocketController controller = new WebSocketController();
        WebSocketController.RetryAutoCaptchaReqDTO reqDTO = new WebSocketController.RetryAutoCaptchaReqDTO();

        ResultObject<String> result = controller.retryAutoCaptcha(reqDTO);

        assertEquals(500, result.getCode());
        assertEquals("账号ID不能为空", result.getMsg());
        assertTrue(result.getData() == null);
    }

    @Test
    void passwordLoginUpdatesCookieAndClearsTokenWhenLoginSucceeds() {
        WebSocketController controller = new WebSocketController();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        AccountService accountService = mock(AccountService.class);
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);

        ReflectionTestUtils.setField(controller, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(controller, "operationLogService", operationLogService);
        when(applicationContext.getBean(PasswordLoginService.class)).thenReturn(passwordLoginService);
        when(applicationContext.getBean(AccountService.class)).thenReturn(accountService);
        when(applicationContext.getBean(WebSocketTokenService.class)).thenReturn(tokenService);
        when(passwordLoginService.tryPasswordLogin(18L)).thenReturn("unb=abc; cookie=value");
        when(accountService.updateAccountCookie(18L, "abc", "unb=abc; cookie=value")).thenReturn(true);

        WebSocketController.PasswordLoginReqDTO reqDTO = new WebSocketController.PasswordLoginReqDTO();
        reqDTO.setXianyuAccountId(18L);

        ResultObject<String> result = controller.passwordLogin(reqDTO);

        assertEquals(200, result.getCode());
        assertEquals("账号密码登录成功，Cookie 已更新", result.getData());
        verify(passwordLoginService).tryPasswordLogin(18L);
        verify(accountService).updateAccountCookie(18L, "abc", "unb=abc; cookie=value");
        verify(tokenService).clearToken(18L);
        verify(tokenService).clearCaptchaWait(18L);
        verify(operationLogService).log(
                eq(18L), eq("VERIFY"), eq("COOKIE"), eq("账号密码登录成功"),
                eq(1), eq("COOKIE"), eq("18"),
                any(), any(), any(), any()
        );
    }
}
