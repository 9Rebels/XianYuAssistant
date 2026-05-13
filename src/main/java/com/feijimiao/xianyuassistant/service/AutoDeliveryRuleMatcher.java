package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Selects the best auto-delivery rule for a trigger message.
 */
public final class AutoDeliveryRuleMatcher {

    private static final Pattern KEYWORD_SPLITTER = Pattern.compile("[,，;；\\n\\r\\t ]+");

    private AutoDeliveryRuleMatcher() {
    }

    public static XianyuGoodsAutoDeliveryConfig select(List<XianyuGoodsAutoDeliveryConfig> rules, String triggerContent) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        String content = normalize(triggerContent);
        XianyuGoodsAutoDeliveryConfig fallback = null;
        XianyuGoodsAutoDeliveryConfig firstEnabled = null;

        for (XianyuGoodsAutoDeliveryConfig rule : rules) {
            if (rule == null || isDisabled(rule)) {
                continue;
            }
            if (firstEnabled == null) {
                firstEnabled = rule;
            }
            List<String> keywords = parseKeywords(rule.getMatchKeyword());
            if (keywords.isEmpty()) {
                if (fallback == null) {
                    fallback = rule;
                }
                continue;
            }
            if (matches(content, keywords, rule.getMatchType())) {
                return rule;
            }
        }
        return fallback != null ? fallback : firstEnabled;
    }

    public static boolean isDisabled(XianyuGoodsAutoDeliveryConfig rule) {
        return rule.getEnabled() != null && rule.getEnabled() == 0;
    }

    static List<String> parseKeywords(String rawKeywords) {
        List<String> keywords = new ArrayList<>();
        if (rawKeywords == null || rawKeywords.trim().isEmpty()) {
            return keywords;
        }
        for (String item : KEYWORD_SPLITTER.split(rawKeywords)) {
            String keyword = normalize(item);
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private static boolean matches(String content, List<String> keywords, Integer matchType) {
        if (content.isEmpty()) {
            return false;
        }
        if (matchType != null && matchType == 2) {
            return keywords.stream().allMatch(content::contains);
        }
        return keywords.stream().anyMatch(content::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
