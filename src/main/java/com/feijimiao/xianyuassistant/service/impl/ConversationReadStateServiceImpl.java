package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuConversationState;
import com.feijimiao.xianyuassistant.mapper.XianyuConversationStateMapper;
import com.feijimiao.xianyuassistant.service.ConversationReadStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 记录在线会话读回执状态。
 */
@Slf4j
@Service
public class ConversationReadStateServiceImpl implements ConversationReadStateService {

    private static final int MAX_RECEIPT_LENGTH = 2000;

    private final XianyuConversationStateMapper stateMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationReadStateServiceImpl(XianyuConversationStateMapper stateMapper) {
        this.stateMapper = stateMapper;
    }

    @Override
    public void markReadReceipt(Long accountId, String decryptedData) {
        if (accountId == null || decryptedData == null || decryptedData.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(decryptedData);
            if (!"2".equals(root.path("2").asText())) {
                return;
            }
            String sid = pickSid(root);
            if (sid == null || sid.isBlank()) {
                log.debug("已读回执缺少sid: accountId={}, payload={}", accountId, trimReceipt(decryptedData));
                return;
            }
            stateMapper.upsertReadReceipt(accountId, sid, pickMessageId(root), pickTimestamp(root), trimReceipt(decryptedData));
            log.info("【账号{}】会话已读回执已记录: sid={}", accountId, sid);
        } catch (Exception e) {
            log.debug("记录已读回执失败: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    @Override
    public void markOutgoingUnread(Long accountId, String sId, Long messageTime) {
        if (accountId == null || sId == null || sId.isBlank()) {
            return;
        }
        Long timestamp = messageTime != null ? messageTime : System.currentTimeMillis();
        try {
            stateMapper.markOutgoingUnread(accountId, sId, timestamp);
        } catch (Exception e) {
            log.debug("标记会话未读失败: accountId={}, sid={}, error={}", accountId, sId, e.getMessage());
        }
    }

    @Override
    public Map<String, XianyuConversationState> findStates(Long accountId, List<String> sIds) {
        Map<String, XianyuConversationState> result = new HashMap<>();
        if (accountId == null || sIds == null || sIds.isEmpty()) {
            return result;
        }
        try {
            for (XianyuConversationState state : stateMapper.findByAccountIdAndSIds(accountId, sIds)) {
                result.put(state.getSId(), state);
            }
        } catch (Exception e) {
            log.debug("查询会话读状态失败: accountId={}, error={}", accountId, e.getMessage());
        }
        return result;
    }

    private String pickSid(JsonNode root) {
        String fixedPathSid = firstText(root.path("1").path("2"), root.path("3"), root.path("sid"));
        if (fixedPathSid != null && !fixedPathSid.isBlank()) {
            return normalizeSid(fixedPathSid);
        }
        return findGoofishSid(root);
    }

    private String pickMessageId(JsonNode root) {
        return firstText(root.path("1").path("3"), root.path("messageId"), root.path("mid"));
    }

    private Long pickTimestamp(JsonNode root) {
        JsonNode timestamp = root.path("1").path("5");
        if (!timestamp.isMissingNode() && timestamp.canConvertToLong()) {
            return timestamp.asLong();
        }
        return System.currentTimeMillis();
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isValueNode()) {
                String value = node.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private String normalizeSid(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.endsWith(".PNM")) {
            return "";
        }
        return value.contains("@") ? value : value + "@goofish";
    }

    private String findGoofishSid(JsonNode root) {
        if (root == null || root.isNull()) {
            return "";
        }
        if (root.isValueNode()) {
            String value = root.asText("");
            return isGoofishSid(value) ? value : "";
        }
        if (root.isObject()) {
            String direct = pickDirectSid(root);
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
        }
        for (JsonNode child : root) {
            String sid = findGoofishSid(child);
            if (sid != null && !sid.isBlank()) {
                return sid;
            }
        }
        return "";
    }

    private String pickDirectSid(JsonNode node) {
        for (String key : List.of("sid", "sId", "cid", "conversationId", "2")) {
            JsonNode valueNode = node.get(key);
            String value = valueNode != null && valueNode.isValueNode() ? valueNode.asText("") : "";
            if (isGoofishSid(value)) {
                return value;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String value = entry.getValue().isValueNode() ? entry.getValue().asText("") : "";
            if (isGoofishSid(value) && entry.getKey().toLowerCase().contains("sid")) {
                return value;
            }
        }
        return "";
    }

    private boolean isGoofishSid(String value) {
        return value != null && value.contains("@goofish") && !value.endsWith(".PNM");
    }

    private String trimReceipt(String value) {
        return value.length() > MAX_RECEIPT_LENGTH ? value.substring(0, MAX_RECEIPT_LENGTH) : value;
    }
}
