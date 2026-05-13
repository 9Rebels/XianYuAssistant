package com.feijimiao.xianyuassistant.exception;

/**
 * 需要滑块验证异常
 */
public class CaptchaRequiredException extends RuntimeException {
    
    private final String captchaUrl;
    
    public CaptchaRequiredException(String captchaUrl) {
        this("需要完成滑块验证", captchaUrl);
    }

    public CaptchaRequiredException(String message, String captchaUrl) {
        super(message);
        this.captchaUrl = captchaUrl;
    }
    
    public String getCaptchaUrl() {
        return captchaUrl;
    }
}
