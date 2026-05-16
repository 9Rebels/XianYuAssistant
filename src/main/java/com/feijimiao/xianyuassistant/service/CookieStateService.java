package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.enums.CookieStatus;

/**
 * Cookie状态收敛服务。
 */
public interface CookieStateService {
    XianyuCookie latestCookie(Long accountId);

    boolean markValid(Long accountId);

    boolean markExpired(Long accountId, boolean sendNotify);

    boolean markInvalid(Long accountId, String reason);

    boolean updateStatus(Long accountId, CookieStatus status, boolean sendNotify);

    boolean isHealthy(XianyuCookie cookie);
}
