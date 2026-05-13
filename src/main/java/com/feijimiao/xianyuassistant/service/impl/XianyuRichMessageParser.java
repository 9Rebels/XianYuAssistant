package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.controller.dto.MsgDTO;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析闲鱼富消息协议，仅用于查询输出层，不改变消息存储和自动回复链路。
 */
@Slf4j
public class XianyuRichMessageParser {

    private static final int CONTENT_TEXT = 1;
    private static final int CONTENT_IMAGE = 2;
    private static final int CONTENT_AUDIO = 3;
    private static final int CONTENT_ITEM_CARD = 7;
    private static final int CONTENT_SYSTEM_TIP = 14;
    private static final int CONTENT_TRADE_CARD = 26;
    private static final int CONTENT_CUSTOM = 101;
    private static final int MAX_VISITED_NODES = 260;
    private static final int MAX_IMAGE_COUNT = 8;
    private static final Set<Integer> IMAGE_CONTENT_TYPES = Set.of(2, 886, 997);
    private static final Set<Integer> CARD_CONTENT_TYPES = Set.of(7, 25, 26, 28, 32);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s,，\"'<>]+");
    private static final Pattern ORDER_PATTERN = Pattern.compile("(?:id|bizOrderId|orderId)=([0-9]+)");

    private final ObjectMapper objectMapper;

    public XianyuRichMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void enrich(MsgDTO dto, XianyuChatMessage message) {
        XianyuRichMessageSource source = buildSource(message.getCompleteMsg());
        int contentType = resolveContentType(message.getContentType(), source);
        String fallbackText = cleanText(message.getMsgContent());

        MsgDTO.CardDTO card = extractCard(contentType, source, fallbackText);
        List<String> imageUrls = extractImageUrls(contentType, source, fallbackText);
        MsgDTO.MediaDTO media = extractAudio(contentType, source);
        String systemText = extractSystemText(source);
        String text = extractText(source, fallbackText);

        dto.setCard(card);
        dto.setImageUrls(imageUrls);
        dto.setMedia(media);
        dto.setMessageKind(resolveKind(contentType, card, imageUrls, media, systemText));
        dto.setDisplayText(resolveDisplayText(dto.getMessageKind(), text, systemText, imageUrls, media, card));
    }

    private String resolveKind(int contentType, MsgDTO.CardDTO card, List<String> imageUrls,
                               MsgDTO.MediaDTO media, String systemText) {
        if (card != null) {
            return contentType == CONTENT_TRADE_CARD ? "trade_card" : "card";
        }
        if (media != null) {
            return "audio";
        }
        if (!imageUrls.isEmpty()) {
            return "image";
        }
        if (contentType == CONTENT_SYSTEM_TIP || hasText(systemText)) {
            return "system_tip";
        }
        return "text";
    }

    private String resolveDisplayText(String kind, String text, String systemText, List<String> imageUrls,
                                      MsgDTO.MediaDTO media, MsgDTO.CardDTO card) {
        if ("system_tip".equals(kind)) {
            return firstText(systemText, text, "系统提示");
        }
        if ("trade_card".equals(kind) || "card".equals(kind)) {
            return firstText(card.getTitle(), card.getSubtitle(), text, "卡片消息");
        }
        if ("audio".equals(kind)) {
            Long durationMs = media.getDurationMs();
            return durationMs != null && durationMs > 0 ? "[语音] " + formatDuration(durationMs) : "[语音]";
        }
        if ("image".equals(kind)) {
            return hasText(text) ? text : "[图片]" + (imageUrls.size() > 1 ? "x" + imageUrls.size() : "");
        }
        return hasText(text) ? text : "[空消息]";
    }

    private XianyuRichMessageSource buildSource(String completeMsg) {
        List<JsonNode> roots = new ArrayList<>();
        JsonNode root = parseJson(completeMsg);
        if (root != null) {
            roots.add(root);
            JsonNode payload = extractContentPayload(root);
            if (payload != null) {
                roots.add(payload);
            }
        }
        return new XianyuRichMessageSource(collectNodes(roots));
    }

    private JsonNode extractContentPayload(JsonNode root) {
        JsonNode contentString = path(root, "1", "6", "3", "5");
        if (contentString != null && contentString.isTextual()) {
            JsonNode payload = parseJson(contentString.asText());
            if (payload != null) {
                return payload;
            }
        }
        if (root.has("contentType")) {
            return root;
        }
        return null;
    }

    private List<JsonNode> collectNodes(List<JsonNode> roots) {
        List<JsonNode> nodes = new ArrayList<>();
        ArrayDeque<JsonNode> queue = new ArrayDeque<>(roots);
        int visited = 0;
        while (!queue.isEmpty() && visited < MAX_VISITED_NODES) {
            JsonNode node = queue.removeFirst();
            visited++;
            if (node == null || node.isNull()) {
                continue;
            }
            nodes.add(node);
            enqueueChildren(queue, node);
        }
        return nodes;
    }

    private void enqueueChildren(ArrayDeque<JsonNode> queue, JsonNode node) {
        if (node.isArray()) {
            node.forEach(queue::addLast);
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            JsonNode nested = value.isTextual() ? parseJson(value.asText()) : null;
            if (nested != null) {
                queue.addLast(nested);
            }
            JsonNode decoded = "data".equals(entry.getKey()) ? parseBase64Json(value) : null;
            if (decoded != null) {
                queue.addLast(decoded);
            }
            queue.addLast(value);
        });
    }

    private int resolveContentType(Integer contentType, XianyuRichMessageSource source) {
        if (contentType != null) {
            return contentType;
        }
        Integer parsed = source.pickInt("contentType");
        return parsed == null ? CONTENT_TEXT : parsed;
    }

    private MsgDTO.CardDTO extractCard(int contentType, XianyuRichMessageSource source, String fallbackText) {
        MsgDTO.CardDTO tradeCard = extractTradeCard(source);
        if (tradeCard != null) {
            return tradeCard;
        }
        MsgDTO.CardDTO itemCard = extractItemCard(source);
        if (itemCard != null) {
            return itemCard;
        }
        if (!CARD_CONTENT_TYPES.contains(contentType) && !looksLikeCard(fallbackText)) {
            return null;
        }
        return extractGenericCard(source, fallbackText);
    }

    private MsgDTO.CardDTO extractTradeCard(XianyuRichMessageSource source) {
        for (JsonNode node : source.nodes()) {
            MsgDTO.CardDTO card = tradeCardFromMain(path(node, "dxCard", "item", "main"));
            if (card != null) {
                return card;
            }
            card = tradeCardFromMain(path(node, "dynamicOperation", "changeContent", "dxCard", "item", "main"));
            if (card != null) {
                return card;
            }
        }
        return null;
    }

    private MsgDTO.CardDTO tradeCardFromMain(JsonNode main) {
        if (main == null || !main.isObject()) {
            return null;
        }
        JsonNode exContent = main.get("exContent");
        if (exContent == null || !exContent.isObject()) {
            return null;
        }
        JsonNode button = exContent.get("button");
        String targetUrl = firstText(text(main, "targetUrl"), text(button, "targetUrl"));
        MsgDTO.CardDTO card = new MsgDTO.CardDTO();
        card.setTitle(cleanText(text(exContent, "title")));
        card.setSubtitle(cleanText(text(exContent, "desc")));
        card.setActionText(cleanText(text(button, "text")));
        card.setUrl(targetUrl);
        card.setTag("交易卡片");
        card.setOrderId(firstText(extractOrderId(targetUrl), text(main, "bizOrderId"), text(main, "orderId")));
        card.setTaskId(text(path(main, "clickParam", "args"), "task_id"));
        return hasCardText(card) ? card : null;
    }

    private MsgDTO.CardDTO extractItemCard(XianyuRichMessageSource source) {
        for (JsonNode node : source.nodes()) {
            JsonNode item = path(node, "itemCard", "item");
            if (item == null || !item.isObject()) {
                continue;
            }
            MsgDTO.CardDTO card = new MsgDTO.CardDTO();
            card.setTitle(cleanText(text(item, "title")));
            card.setSubtitle(cleanText(firstText(text(item, "price"), text(item, "itemTip"))));
            card.setImageUrl(text(item, "mainPic"));
            card.setUrl(text(path(node, "itemCard", "action", "page"), "url"));
            card.setTag("商品卡片");
            return hasCardText(card) ? card : null;
        }
        return null;
    }

    private MsgDTO.CardDTO extractGenericCard(XianyuRichMessageSource source, String fallbackText) {
        MsgDTO.CardDTO card = new MsgDTO.CardDTO();
        card.setTitle(source.pickText("title", "mainTitle", "itemTitle", "itemName", "goodsTitle", "subject", "bizTitle"));
        card.setSubtitle(source.pickText("firstLineText", "subTitle", "subtitle", "desc", "content", "reminderContent",
                "tip", "price", "statusText", "orderStatus", "bizDesc"));
        card.setActionText(source.pickText("buttonText", "actionText", "actionName", "btnText"));
        card.setImageUrl(source.pickUrl("imgUrl", "picUrl", "image", "imageUrl", "cover", "coverPic", "itemPic", "itemImage", "goodsCoverPic"));
        card.setUrl(source.pickBusinessUrl());
        card.setTag(source.pickText("tag", "cardTypeName", "bizTypeName"));
        card.setOrderId(firstText(extractOrderId(card.getUrl()), source.pickText("bizOrderId", "orderId")));
        if (!hasText(card.getTitle()) && hasText(fallbackText) && !looksLikeCard(fallbackText)) {
            card.setTitle(fallbackText);
        }
        if (!hasText(card.getTitle()) && !hasText(card.getSubtitle()) && !hasText(card.getImageUrl())) {
            card.setTitle("卡片消息");
        }
        return card;
    }

    private List<String> extractImageUrls(int contentType, XianyuRichMessageSource source, String fallbackText) {
        if (CARD_CONTENT_TYPES.contains(contentType) && contentType != CONTENT_CUSTOM) {
            return List.of();
        }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (JsonNode node : source.nodes()) {
            collectImageUrl(urls, text(node, "image_url"));
            collectImageUrl(urls, text(node, "imageUrl"));
            collectImageUrl(urls, text(node, "url"));
            collectImageUrl(urls, text(node, "picUrl"));
        }
        Matcher matcher = URL_PATTERN.matcher(fallbackText == null ? "" : fallbackText);
        while (matcher.find()) {
            collectImageUrl(urls, matcher.group());
        }
        if (IMAGE_CONTENT_TYPES.contains(contentType) && urls.isEmpty()) {
            return source.pickAllUrls(MAX_IMAGE_COUNT);
        }
        return urls.stream().limit(MAX_IMAGE_COUNT).toList();
    }

    private MsgDTO.MediaDTO extractAudio(int contentType, XianyuRichMessageSource source) {
        if (contentType != CONTENT_AUDIO && !source.hasObjectKey("audio")) {
            return null;
        }
        for (JsonNode node : source.nodes()) {
            JsonNode audio = node.has("audio") ? node.get("audio") : node;
            String url = firstText(text(audio, "url"), text(audio, "audio_url"), text(audio, "audioUrl"));
            if (!hasText(url)) {
                continue;
            }
            MsgDTO.MediaDTO media = new MsgDTO.MediaDTO();
            media.setType("audio");
            media.setUrl(url);
            media.setDurationMs(longValue(audio, "duration", "duration_ms", "durationMs"));
            return media;
        }
        return null;
    }

    private String extractSystemText(XianyuRichMessageSource source) {
        String systemTip = source.pickText("tipText");
        if (hasText(systemTip)) {
            return systemTip;
        }
        for (JsonNode node : source.nodes()) {
            String tip = text(path(node, "tip"), "tip");
            if (hasText(tip)) {
                return cleanText(tip);
            }
        }
        return "";
    }

    private String extractText(XianyuRichMessageSource source, String fallbackText) {
        String protocolText = source.pickText("text", "content", "reminderContent");
        return firstText(protocolText, fallbackText);
    }

    private void collectImageUrl(Set<String> urls, String value) {
        if (!hasText(value)) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(value);
        String url = matcher.find() ? matcher.group() : value;
        url = cleanUrl(url);
        if (isImageUrl(url)) {
            urls.add(url);
        }
    }

    private boolean isImageUrl(String url) {
        return hasText(url) && (url.matches("(?i).+\\.(png|jpe?g|gif|webp|bmp|avif)(\\?.*)?$")
                || url.matches("(?i).*(alicdn\\.com|tbcdn\\.cn|mmstat\\.com).*"));
    }

    private String extractOrderId(String url) {
        if (!hasText(url)) {
            return "";
        }
        String decoded = url;
        try {
            decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("交易卡片链接URL解码失败", e);
        }
        Matcher matcher = ORDER_PATTERN.matcher(decoded);
        return matcher.find() ? matcher.group(1) : "";
    }

    private JsonNode parseJson(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parseBase64Json(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().length() < 8) {
            return null;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(value.asText()), StandardCharsets.UTF_8);
            return parseJson(decoded);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode path(JsonNode node, String... keys) {
        JsonNode current = node;
        for (String key : keys) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(key);
        }
        return current;
    }

    private String text(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode value = node.get(key);
        return value != null && value.isValueNode() ? cleanText(value.asText()) : "";
    }

    private Long longValue(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
            if (value != null && value.isTextual() && value.asText().matches("\\d+")) {
                return Long.parseLong(value.asText());
            }
        }
        return null;
    }

    private String cleanUrl(String url) {
        return hasText(url) ? url.replaceAll("[)\\]}，,。]+$", "") : "";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasCardText(MsgDTO.CardDTO card) {
        return hasText(card.getTitle()) || hasText(card.getSubtitle()) || hasText(card.getActionText()) || hasText(card.getUrl());
    }

    private boolean looksLikeCard(String text) {
        return hasText(text) && (text.contains("卡片消息") || text.contains("商品卡片") || text.contains("订单卡片"));
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs > 1000 ? Math.round(durationMs / 1000.0) : durationMs;
        return seconds + "秒";
    }
}
