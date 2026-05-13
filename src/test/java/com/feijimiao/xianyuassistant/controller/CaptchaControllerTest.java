package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.CaptchaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaControllerTest {

    @Mock
    private CaptchaService captchaService;

    @Mock
    private XianyuCookieMapper cookieMapper;

    @InjectMocks
    private CaptchaController captchaController;

    @Test
    void detectCaptchaReturnsAutoFailureMessageWithoutManualFields() {
        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("unb=1");
        when(cookieMapper.selectByAccountId(1L)).thenReturn(cookie);
        when(captchaService.handleCaptcha(1L, "unb=1", "https://www.goofish.com/im", true))
                .thenReturn(CaptchaService.CaptchaResult.autoFailed());

        ResultObject<Map<String, Object>> result = captchaController.detectCaptcha(Map.of(
                "xianyuAccountId", 1L,
                "targetUrl", "https://www.goofish.com/im",
                "autoVerify", true
        ));

        assertEquals(200, result.getCode());
        assertTrue((Boolean) result.getData().get("needCaptcha"));
        assertFalse((Boolean) result.getData().get("autoVerifySuccess"));
        assertFalse((Boolean) result.getData().get("needManual"));
        assertNull(result.getData().get("manualVerifyUrl"));
        assertNull(result.getData().get("captchaUrl"));
        assertEquals(CaptchaService.AUTO_SLIDER_FAILED_MESSAGE, result.getData().get("message"));
        verify(captchaService).handleCaptcha(eq(1L), eq("unb=1"), eq("https://www.goofish.com/im"), eq(true));
    }
}
