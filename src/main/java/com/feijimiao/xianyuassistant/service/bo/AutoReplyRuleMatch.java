package com.feijimiao.xianyuassistant.service.bo;

import lombok.Data;

import java.util.List;

/**
 * 自动回复规则命中结果
 */
@Data
public class AutoReplyRuleMatch {

    private Long ruleId;

    private String ruleName;

    private String matchedKeyword;

    private Integer replyType;

    private String replyContent;

    private List<String> imageUrls;

    private Boolean defaultReply;
}
