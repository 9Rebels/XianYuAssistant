package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.service.DeliveryMessageSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单状态消息监听器
 */
@Slf4j
@Component
public class ChatMessageEventOrderStatusListener {

    private static final int USER_TEXT_TYPE = 1;
    private static final int MAX_DEDUP_SIZE = 5000;

    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    @Autowired
    private DeliveryMessageSettingService deliveryMessageSettingService;

    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessageData message = event.getMessageData();
        try {
            if (!isSystemLikeMessage(message) || isBlank(message.getSId()) || isBlank(message.getMsgContent())) {
                return;
            }

            String eventType = resolveEventType(message.getMsgContent());
            if (eventType == null || !markProcessed(message, eventType)) {
                return;
            }

            deliveryMessageSettingService.sendOrderStatusMessage(
                    eventType,
                    message.getXianyuAccountId(),
                    message.getSId(),
                    message.getXyGoodsId(),
                    message.getOrderId());
        } catch (Exception e) {
            log.error("【账号{}】处理订单状态消息异常: pnmId={}", message.getXianyuAccountId(), message.getPnmId(), e);
        }
    }

    private boolean isSystemLikeMessage(ChatMessageData message) {
        return message.getContentType() == null || message.getContentType() != USER_TEXT_TYPE;
    }

    private String resolveEventType(String content) {
        if (isRefundingMessage(content)) {
            return DeliveryMessageSettingService.EVENT_REFUNDING;
        }
        if (isReceivedMessage(content)) {
            return DeliveryMessageSettingService.EVENT_RECEIVED;
        }
        return null;
    }

    private boolean isRefundingMessage(String content) {
        return content.contains("退款") && (content.contains("申请") || content.contains("退款中") || content.contains("处理中"));
    }

    private boolean isReceivedMessage(String content) {
        return content.contains("确认收货") || content.contains("交易成功") || content.contains("已收货");
    }

    private boolean markProcessed(ChatMessageData message, String eventType) {
        String pnmId = !isBlank(message.getPnmId()) ? message.getPnmId() : String.valueOf(message.getMessageTime());
        String key = message.getXianyuAccountId() + ":" + eventType + ":" + pnmId;
        if (processedKeys.size() > MAX_DEDUP_SIZE) {
            processedKeys.clear();
        }
        return processedKeys.add(key);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
