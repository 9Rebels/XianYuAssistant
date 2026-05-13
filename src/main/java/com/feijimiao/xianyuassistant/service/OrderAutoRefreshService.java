package com.feijimiao.xianyuassistant.service;

/**
 * Schedules a delayed single-order refresh after a new order appears.
 */
public interface OrderAutoRefreshService {

    void scheduleRefresh(Long accountId, String orderId);

    int getPendingTaskCount();
}
