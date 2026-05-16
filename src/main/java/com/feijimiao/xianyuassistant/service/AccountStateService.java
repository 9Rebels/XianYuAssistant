package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;

/**
 * 账号状态收敛服务。
 */
public interface AccountStateService {
    boolean markNormal(Long accountId, String reason);

    boolean restoreNormalIfCaptchaRequired(Long accountId, String reason);

    boolean markCaptchaRequired(Long accountId, String reason);

    boolean isNormal(XianyuAccount account);

    boolean isCaptchaRequired(XianyuAccount account);
}
