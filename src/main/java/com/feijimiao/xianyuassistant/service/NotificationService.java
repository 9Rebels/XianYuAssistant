package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuNotificationLog;

import java.util.List;

/**
 * 本地通知服务
 */
public interface NotificationService {

    String EVENT_DELIVERY_SUCCESS = "auto_delivery_success";
    String EVENT_DELIVERY_FAIL = "auto_delivery_fail";
    String EVENT_STOCK_WARNING = "stock_warning";
    String EVENT_HOURLY_REPORT = "hourly_report";
    String EVENT_WS_DISCONNECT = "ws_disconnect";
    String EVENT_COOKIE_EXPIRE = "cookie_expire";
    String EVENT_CAPTCHA_REQUIRED = "captcha_required";
    String EVENT_CAPTCHA_SUCCESS = "captcha_success";
    String EVENT_TEST = "test";

    void notifyEvent(String eventType, String title, String content);

    boolean sendTest(String title, String content);

    List<XianyuNotificationLog> latestLogs();
}
