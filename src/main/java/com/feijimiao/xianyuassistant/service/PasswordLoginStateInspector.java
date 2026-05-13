package com.feijimiao.xianyuassistant.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
class PasswordLoginStateInspector {
    private static final List<String> REQUIRED_COOKIE_FIELDS = List.of(
            "unb",
            "sgcookie",
            "cookie2",
            "_m_h5_tk",
            "_m_h5_tk_enc",
            "t"
    );

    boolean hasHardReject(String text) {
        String value = normalize(text);
        return value.contains("验证失败")
                || value.contains("点击框体重试")
                || value.contains("error:")
                || value.contains("fail_code")
                || value.contains("purecaptcha");
    }

    boolean hasManualVerification(String text, String url) {
        String value = normalize(text) + " " + normalize(url);
        return value.contains("扫码")
                || value.contains("二维码")
                || value.contains("人脸")
                || value.contains("刷脸")
                || value.contains("短信")
                || value.contains("验证码")
                || value.contains("身份验证")
                || value.contains("security")
                || value.contains("verify");
    }

    boolean hasLoginFormHint(String text) {
        String value = normalize(text);
        return value.contains("密码登录")
                || value.contains("忘记密码")
                || value.contains("fm-login")
                || value.contains("手机号")
                || value.contains("账号名")
                || value.contains("登录");
    }

    boolean hasCompletedLoginCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return false;
        }
        for (String field : REQUIRED_COOKIE_FIELDS) {
            String value = cookies.get(field);
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    List<String> missingRequiredCookieFields(Map<String, String> cookies) {
        Map<String, String> source = cookies == null ? Map.of() : cookies;
        return REQUIRED_COOKIE_FIELDS.stream()
                .filter(field -> {
                    String value = source.get(field);
                    return value == null || value.isBlank();
                })
                .toList();
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase();
    }
}
