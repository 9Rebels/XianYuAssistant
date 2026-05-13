package com.feijimiao.xianyuassistant.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自动回复规则查询请求
 */
@Data
public class AutoReplyRuleQueryReqDTO {

    @NotNull(message = "闲鱼账号ID不能为空")
    private Long xianyuAccountId;

    private String xyGoodsId;

    private Boolean includeGlobal = true;
}
