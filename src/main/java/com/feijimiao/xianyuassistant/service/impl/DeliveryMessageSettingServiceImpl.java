package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.service.DeliveryMessageSettingService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.SentMessageSaveService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发货配置消息服务实现
 */
@Slf4j
@Service
public class DeliveryMessageSettingServiceImpl implements DeliveryMessageSettingService {

    private static final String KAMI_SINGLE_SEND_ENABLED = "delivery_kami_single_send_enabled";
    private static final String ENABLED_SUFFIX = "_enabled";
    private static final String MESSAGE_TYPE_SUFFIX = "_message_type";
    private static final String CONTENT_SUFFIX = "_content";
    private static final String TYPE_IMAGE = "image";
    private static final int IMAGE_WIDTH = 800;
    private static final int IMAGE_HEIGHT = 800;

    @Autowired
    private SysSettingService sysSettingService;

    @Lazy
    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private SentMessageSaveService sentMessageSaveService;

    @Autowired
    private OperationLogService operationLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isKamiSingleSendEnabled() {
        return isEnabled(KAMI_SINGLE_SEND_ENABLED);
    }

    @Override
    public void sendKamiSingleMessage(Long accountId, String cid, String toId, String rawKamiContent, String xyGoodsId) {
        if (!isKamiSingleSendEnabled() || isBlank(rawKamiContent)) {
            return;
        }
        boolean success = webSocketService.sendMessage(accountId, cid, toId, rawKamiContent);
        if (success) {
            sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, rawKamiContent, xyGoodsId);
            log.info("【账号{}】卡密单发成功: xyGoodsId={}", accountId, xyGoodsId);
            logOrderStatusMessage(accountId, xyGoodsId, null, cid, "kami_single", "卡密单独发送成功", true, 1, rawKamiContent.length(), null);
        } else {
            log.warn("【账号{}】卡密单发失败: xyGoodsId={}", accountId, xyGoodsId);
            logOrderStatusMessage(accountId, xyGoodsId, null, cid, "kami_single", "卡密单独发送失败", false, 1, rawKamiContent.length(), "消息发送失败");
        }
    }

    @Override
    public void sendOrderStatusMessage(String eventType, XianyuGoodsOrder order) {
        if (order == null) {
            return;
        }
        sendOrderStatusMessage(eventType, order.getXianyuAccountId(), order.getSid(), order.getXyGoodsId(), order.getOrderId());
    }

    @Override
    public void sendOrderStatusMessage(String eventType, Long accountId, String sId, String xyGoodsId, String orderId) {
        if (accountId == null || isBlank(sId) || !isEnabled(keyPrefix(eventType) + ENABLED_SUFFIX)) {
            return;
        }
        String content = sysSettingService.getSettingValue(keyPrefix(eventType) + CONTENT_SUFFIX);
        if (isBlank(content)) {
            log.warn("【账号{}】订单状态消息已启用但内容为空: eventType={}, orderId={}", accountId, eventType, orderId);
            return;
        }
        String cid = cleanConversationId(sId);
        String messageType = sysSettingService.getSettingValue(keyPrefix(eventType) + MESSAGE_TYPE_SUFFIX);
        if (TYPE_IMAGE.equals(messageType)) {
            sendImages(accountId, cid, cid, content, xyGoodsId, eventType, orderId);
            return;
        }
        sendText(accountId, cid, cid, content, xyGoodsId, eventType, orderId);
    }

    private void sendText(Long accountId, String cid, String toId, String content, String xyGoodsId, String eventType, String orderId) {
        boolean success = webSocketService.sendMessage(accountId, cid, toId, content);
        if (success) {
            sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
            log.info("【账号{}】订单状态文本消息发送成功: eventType={}, orderId={}", accountId, eventType, orderId);
            logOrderStatusMessage(accountId, xyGoodsId, orderId, cid, eventType, "订单状态文本消息发送成功", true, 1, content.length(), null);
        } else {
            log.warn("【账号{}】订单状态文本消息发送失败: eventType={}, orderId={}", accountId, eventType, orderId);
            logOrderStatusMessage(accountId, xyGoodsId, orderId, cid, eventType, "订单状态文本消息发送失败", false, 1, content.length(), "消息发送失败");
        }
    }

    private void sendImages(Long accountId, String cid, String toId, String content, String xyGoodsId, String eventType, String orderId) {
        List<String> imageUrls = splitImageUrls(content);
        for (String imageUrl : imageUrls) {
            boolean success = webSocketService.sendImageMessage(accountId, cid, toId, imageUrl, IMAGE_WIDTH, IMAGE_HEIGHT);
            if (success) {
                sentMessageSaveService.saveManualImageReply(accountId, cid, toId, imageUrl, xyGoodsId);
                log.info("【账号{}】订单状态图片消息发送成功: eventType={}, orderId={}", accountId, eventType, orderId);
                logOrderStatusMessage(accountId, xyGoodsId, orderId, cid, eventType, "订单状态图片消息发送成功", true, imageUrls.size(), imageUrl.length(), null);
            } else {
                log.warn("【账号{}】订单状态图片消息发送失败: eventType={}, orderId={}", accountId, eventType, orderId);
                logOrderStatusMessage(accountId, xyGoodsId, orderId, cid, eventType, "订单状态图片消息发送失败", false, imageUrls.size(), imageUrl.length(), "图片发送失败");
            }
        }
    }

    private void logOrderStatusMessage(Long accountId, String xyGoodsId, String orderId, String cid, String eventType,
                                       String desc, boolean success, int messageCount, int contentLength, String errorMessage) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("xyGoodsId", safeText(xyGoodsId));
            params.put("orderId", safeText(orderId));
            params.put("cid", safeText(cid));
            params.put("eventType", safeText(eventType));
            params.put("messageCount", messageCount);
            params.put("contentLength", contentLength);
            operationLogService.log(
                    accountId,
                    OperationConstants.Type.SEND,
                    OperationConstants.Module.MESSAGE,
                    desc,
                    success ? OperationConstants.Status.SUCCESS : OperationConstants.Status.FAIL,
                    OperationConstants.TargetType.MESSAGE,
                    !isBlank(orderId) ? orderId : cid,
                    objectMapper.writeValueAsString(params),
                    null,
                    errorMessage,
                    null);
        } catch (Exception e) {
            log.warn("【账号{}】记录订单状态消息操作日志失败: eventType={}, orderId={}", accountId, eventType, orderId, e);
        }
    }

    private boolean isEnabled(String key) {
        String value = sysSettingService.getSettingValue(key);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private String keyPrefix(String eventType) {
        return "delivery_order_" + eventType;
    }

    private String cleanConversationId(String sId) {
        return sId.replace("@goofish", "");
    }

    private List<String> splitImageUrls(String value) {
        return Arrays.stream(value.split("[,\\n\\r]+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
