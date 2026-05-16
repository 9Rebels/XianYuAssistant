package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;

/**
 * Refreshes the seller workbench session used by fish-shop order APIs.
 */
public interface SellerSessionRefreshService {

    CookieRecoveryResult refreshSellerSession(Long accountId, String cookieText);
}
