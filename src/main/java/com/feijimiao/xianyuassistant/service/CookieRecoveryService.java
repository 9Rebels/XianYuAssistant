package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;

public interface CookieRecoveryService {
    CookieRecoveryResult recover(Long accountId, String operationName, String reason);
}
