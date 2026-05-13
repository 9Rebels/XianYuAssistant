package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.sse.SseEventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class ChatMessageEventSseListener {

    @Autowired
    private SseEventBus sseEventBus;

    public void broadcastSavedMessage(ChatMessageData data, Long messageId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accountId", data.getXianyuAccountId());
            payload.put("xianyuAccountId", data.getXianyuAccountId());
            payload.put("id", messageId);
            payload.put("messageId", messageId);
            payload.put("sId", data.getSId());
            payload.put("sid", data.getSId());
            payload.put("contentType", data.getContentType());
            payload.put("senderUserId", data.getSenderUserId());
            payload.put("messageTime", data.getMessageTime());
            String content = data.getMsgContent();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200);
            }
            payload.put("msgContent", content);
            sseEventBus.broadcast("message", payload);
        } catch (Exception e) {
            log.debug("SSE消息推送异常: {}", e.getMessage());
        }
    }
}
