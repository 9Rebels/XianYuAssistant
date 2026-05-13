package com.feijimiao.xianyuassistant.exception;

import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;

public class CookieRecoveryRequiredException extends RuntimeException {
    private final CookieRecoveryResult recoveryResult;

    public CookieRecoveryRequiredException(CookieRecoveryResult recoveryResult) {
        super(recoveryResult != null ? recoveryResult.getMessage() : "Cookie自动恢复失败");
        this.recoveryResult = recoveryResult;
    }

    public CookieRecoveryResult getRecoveryResult() {
        return recoveryResult;
    }
}
