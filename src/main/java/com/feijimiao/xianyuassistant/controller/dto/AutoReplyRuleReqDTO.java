package com.feijimiao.xianyuassistant.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自动回复规则保存请求
 */
@Data
public class AutoReplyRuleReqDTO {

    private Long id;

    @NotNull(message = "闲鱼账号ID不能为空")
    private Long xianyuAccountId;

    private String xyGoodsId;

    private String ruleName;

    private String keywords;

    private Integer matchType = 1;

    private Integer replyType = 1;

    private String replyContent;

    private String imageUrls;

    private Integer priority = 100;

    private Integer enabled = 1;

    private Integer isDefault = 0;
}
