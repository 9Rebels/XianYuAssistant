package com.feijimiao.xianyuassistant.enums;

import lombok.Getter;

/**
 * 闲鱼账号运行状态。
 */
@Getter
public enum AccountStatus {
    CAPTCHA_REQUIRED(-2, "需要人机验证"),
    /**
     * 兼容历史数据的只读状态；阶段1不新增写入路径。
     */
    PHONE_VERIFICATION_REQUIRED(-1, "需要手机号验证"),
    NORMAL(1, "正常");

    private final Integer code;
    private final String description;

    AccountStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AccountStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AccountStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static boolean isNormal(Integer code) {
        return NORMAL.code.equals(code);
    }

    public static boolean isCaptchaRequired(Integer code) {
        return CAPTCHA_REQUIRED.code.equals(code);
    }
}
