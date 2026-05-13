package com.feijimiao.xianyuassistant.exception;

/**
 * Cookie未找到异常
 */
public class CookieNotFoundException extends RuntimeException {
    
    public CookieNotFoundException(String message) {
        super(message);
    }
}
