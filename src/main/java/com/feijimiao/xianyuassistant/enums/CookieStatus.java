package com.feijimiao.xianyuassistant.enums;

import lombok.Getter;

/**
 * Cookie状态。
 */
@Getter
public enum CookieStatus {
    VALID(1, "有效"),
    EXPIRED(2, "过期"),
    INVALID(3, "失效");

    private final Integer code;
    private final String description;

    CookieStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static CookieStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CookieStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static boolean isValid(Integer code) {
        return VALID.code.equals(code);
    }

    public static boolean isExpired(Integer code) {
        return EXPIRED.code.equals(code);
    }

    public static boolean isInvalid(Integer code) {
        return INVALID.code.equals(code);
    }
}
