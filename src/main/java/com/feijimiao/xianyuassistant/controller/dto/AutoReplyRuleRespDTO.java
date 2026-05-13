package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动回复规则响应
 */
@Data
public class AutoReplyRuleRespDTO {

    private Long id;

    private Long xianyuAccountId;

    private String xyGoodsId;

    private String ruleName;

    private String keywords;

    private Integer matchType;

    private Integer replyType;

    private String replyContent;

    private String imageUrls;

    private Integer priority;

    private Integer enabled;

    private Integer isDefault;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
