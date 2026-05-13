package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class XianyuRichMessageSource {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s,，\"'<>]+");

    private final List<JsonNode> nodes;

    XianyuRichMessageSource(List<JsonNode> nodes) {
        this.nodes = nodes;
    }

    List<JsonNode> nodes() {
        return nodes;
    }

    boolean hasObjectKey(String key) {
        return nodes.stream().anyMatch(node -> node.isObject() && node.has(key));
    }

    Integer pickInt(String key) {
        for (JsonNode node : nodes) {
            JsonNode value = node.get(key);
            if (value != null && value.isInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    String pickText(String... keys) {
        for (JsonNode node : nodes) {
            for (String key : keys) {
                String value = text(node, key);
                if (hasText(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    String pickUrl(String... keys) {
        for (String key : keys) {
            for (JsonNode node : nodes) {
                String value = text(node, key);
                if (!hasText(value)) {
                    continue;
                }
                Matcher matcher = URL_PATTERN.matcher(value);
                String url = cleanUrl(matcher.find() ? matcher.group() : value);
                if (hasText(url)) {
                    return url;
                }
            }
        }
        return "";
    }

    String pickBusinessUrl() {
        String preferred = pickUrl("pcJumpUrl", "targetUrl", "reminderUrl", "jumpUrl", "actionUrl", "itemUrl", "detailUrl");
        if (isBusinessUrl(preferred)) {
            return preferred;
        }
        String generic = pickUrl("url");
        return isBusinessUrl(generic) ? generic : "";
    }

    List<String> pickAllUrls(int limit) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                continue;
            }
            node.fields().forEachRemaining(entry -> collectUrls(urls, entry.getValue(), limit));
        }
        return urls.stream().limit(limit).toList();
    }

    private void collectUrls(LinkedHashSet<String> urls, JsonNode value, int limit) {
        if (!value.isTextual()) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(value.asText());
        while (matcher.find() && urls.size() < limit) {
            urls.add(cleanUrl(matcher.group()));
        }
    }

    private String text(JsonNode node, String key) {
        if (node == null || !node.isObject()) {
            return "";
        }
        JsonNode value = node.get(key);
        return value != null && value.isValueNode() ? cleanText(value.asText()) : "";
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private String cleanUrl(String url) {
        return hasText(url) ? url.replaceAll("[)\\]}，,。]+$", "") : "";
    }

    private boolean isBusinessUrl(String url) {
        return hasText(url)
                && !url.matches("(?i).+\\.(zip|js|css|json|png|jpe?g|webp|gif)(\\?.*)?$")
                && !url.matches("(?i).*(dinamicx\\.alibabausercontent\\.com|template|schema).*");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
