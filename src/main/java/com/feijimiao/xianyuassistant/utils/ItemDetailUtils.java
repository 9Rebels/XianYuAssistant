package com.feijimiao.xianyuassistant.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;

/**
 * 商品详情工具类
 */
@Slf4j
public class ItemDetailUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SUCCESS_RET_KEYWORD = "SUCCESS";
    
    /**
     * 从商品详情JSON中提取desc字段
     * 
     * @param detailJson 商品详情JSON字符串
     * @return 提取的desc字段内容，如果提取失败则返回null
     */
    public static String extractDescFromDetailJson(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }

        String normalizedJson = detailJson.trim();
        if (!looksLikeJson(normalizedJson)) {
            return normalizedJson;
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(normalizedJson);
            if (hasFailedRet(rootNode)) {
                log.warn("商品详情API返回失败: ret={}", rootNode.get("ret"));
                return null;
            }
            
            JsonNode payloadNode = resolvePayloadNode(rootNode);
            String desc = extractDesc(payloadNode);
            if (desc != null) {
                log.info("成功提取desc字段，长度: {}", desc.length());
                return desc;
            }
            
            log.warn("无法提取desc字段，data字段: {}", describeFields(payloadNode));
            return null;
        } catch (Exception e) {
            log.warn("解析商品详情JSON失败: {}", e.getMessage());
            return null;
        }
    }

    private static boolean looksLikeJson(String value) {
        return value.startsWith("{") || value.startsWith("[");
    }

    private static boolean hasFailedRet(JsonNode rootNode) {
        JsonNode retNode = rootNode.get("ret");
        if (retNode == null || !retNode.isArray() || retNode.isEmpty()) {
            return false;
        }
        String firstRet = retNode.get(0).asText("");
        return !firstRet.contains(SUCCESS_RET_KEYWORD);
    }

    private static JsonNode resolvePayloadNode(JsonNode rootNode) {
        JsonNode dataNode = rootNode.get("data");
        if (dataNode != null && dataNode.isObject()) {
            return dataNode;
        }
        return rootNode;
    }

    private static String extractDesc(JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            return null;
        }
        JsonNode itemDONode = payloadNode.get("itemDO");
        if (itemDONode == null || itemDONode.isNull()) {
            return null;
        }
        JsonNode descNode = itemDONode.get("desc");
        if (descNode == null || descNode.isNull()) {
            return null;
        }
        String desc = descNode.asText();
        return desc.isBlank() ? null : desc;
    }

    private static String describeFields(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "[]";
        }
        StringBuilder fields = new StringBuilder("[");
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            if (fields.length() > 1) {
                fields.append(", ");
            }
            fields.append(fieldNames.next());
        }
        fields.append("]");
        return fields.toString();
    }
}
