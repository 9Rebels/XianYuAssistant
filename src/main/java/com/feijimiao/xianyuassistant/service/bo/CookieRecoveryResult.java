package com.feijimiao.xianyuassistant.service.bo;

import lombok.Data;

@Data
public class CookieRecoveryResult {
    private boolean success;
    private boolean attempted;
    private boolean needCaptcha;
    private boolean needManual;
    private String cookieText;
    private String message;
    private String manualVerifyUrl;
    private String captchaUrl;
    private String sessionId;

    public static CookieRecoveryResult success(String cookieText, String message) {
        CookieRecoveryResult result = new CookieRecoveryResult();
        result.setSuccess(true);
        result.setAttempted(true);
        result.setCookieText(cookieText);
        result.setMessage(message);
        return result;
    }

    public static CookieRecoveryResult manual(String message, String sessionId, String manualVerifyUrl) {
        CookieRecoveryResult result = new CookieRecoveryResult();
        result.setAttempted(true);
        result.setNeedCaptcha(true);
        result.setNeedManual(true);
        result.setMessage(message);
        result.setSessionId(sessionId);
        result.setManualVerifyUrl(manualVerifyUrl);
        result.setCaptchaUrl(manualVerifyUrl);
        return result;
    }

    public static CookieRecoveryResult failed(String message) {
        CookieRecoveryResult result = new CookieRecoveryResult();
        result.setAttempted(true);
        result.setMessage(message);
        return result;
    }
}
