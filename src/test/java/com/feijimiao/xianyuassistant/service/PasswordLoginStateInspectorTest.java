package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordLoginStateInspectorTest {

    private final PasswordLoginStateInspector inspector = new PasswordLoginStateInspector();

    @Test
    void detectsHardRejectText() {
        assertTrue(inspector.hasHardReject("验证失败，点击框体重试(error:zh6Tnd)"));
        assertTrue(inspector.hasHardReject("{\"fail_code\":\"punish_captcha\"}"));
        assertFalse(inspector.hasHardReject("请拖动滑块完成验证"));
    }

    @Test
    void requiresCompleteSessionCookies() {
        assertTrue(inspector.hasCompletedLoginCookies(Map.of(
                "unb", "1",
                "sgcookie", "sg",
                "cookie2", "ck",
                "_m_h5_tk", "tk",
                "_m_h5_tk_enc", "enc",
                "t", "t-value"
        )));
        assertFalse(inspector.hasCompletedLoginCookies(Map.of(
                "unb", "1",
                "sgcookie", "sg"
        )));
    }

    @Test
    void detectsManualVerificationHints() {
        assertTrue(inspector.hasManualVerification("请使用手机扫码完成身份验证", ""));
        assertTrue(inspector.hasManualVerification("输入短信验证码", ""));
        assertTrue(inspector.hasManualVerification("", "https://passport.goofish.com/security/verify"));
        assertFalse(inspector.hasManualVerification("登录 订单 消息 发闲置", "https://www.goofish.com/im"));
    }
}
