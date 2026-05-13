package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AutoDeliveryRuleMatcherTest {

    @Test
    void selectsFirstMatchedKeywordRuleBeforeDefaultRule() {
        XianyuGoodsAutoDeliveryConfig spec = rule(1L, "红色,XL", 1, 1);
        XianyuGoodsAutoDeliveryConfig fallback = rule(2L, "", 1, 1);

        XianyuGoodsAutoDeliveryConfig selected = AutoDeliveryRuleMatcher.select(
                List.of(spec, fallback),
                "买家已付款，规格：红色 M"
        );

        assertSame(spec, selected);
    }

    @Test
    void allKeywordModeRequiresEveryKeyword() {
        XianyuGoodsAutoDeliveryConfig all = rule(1L, "红色 XL", 2, 1);
        XianyuGoodsAutoDeliveryConfig fallback = rule(2L, "", 1, 1);

        XianyuGoodsAutoDeliveryConfig selected = AutoDeliveryRuleMatcher.select(
                List.of(all, fallback),
                "规格：红色 M"
        );

        assertSame(fallback, selected);
    }

    @Test
    void skipsDisabledRulesAndFallsBackToEnabledDefault() {
        XianyuGoodsAutoDeliveryConfig disabled = rule(1L, "红色", 1, 0);
        XianyuGoodsAutoDeliveryConfig fallback = rule(2L, "", 1, 1);

        XianyuGoodsAutoDeliveryConfig selected = AutoDeliveryRuleMatcher.select(
                List.of(disabled, fallback),
                "规格：红色"
        );

        assertEquals(2L, selected.getId());
    }

    private XianyuGoodsAutoDeliveryConfig rule(Long id, String keywords, Integer matchType, Integer enabled) {
        XianyuGoodsAutoDeliveryConfig rule = new XianyuGoodsAutoDeliveryConfig();
        rule.setId(id);
        rule.setMatchKeyword(keywords);
        rule.setMatchType(matchType);
        rule.setEnabled(enabled);
        return rule;
    }
}
