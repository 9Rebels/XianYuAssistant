package com.feijimiao.xianyuassistant.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 自动回复规则批量导入请求
 */
@Data
public class AutoReplyRuleBatchImportReqDTO {

    @Valid
    @NotEmpty(message = "导入规则不能为空")
    private List<AutoReplyRuleReqDTO> rules;
}
