package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;

/**
 * 发货配置消息服务
 */
public interface DeliveryMessageSettingService {

    String EVENT_SHIPPED = "shipped";

    String EVENT_RECEIVED = "received";

    String EVENT_REFUNDING = "refunding";

    boolean isKamiSingleSendEnabled();

    void sendKamiSingleMessage(Long accountId, String cid, String toId, String rawKamiContent, String xyGoodsId);

    void sendOrderStatusMessage(String eventType, XianyuGoodsOrder order);

    void sendOrderStatusMessage(String eventType, Long accountId, String sId, String xyGoodsId, String orderId);
}
