package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 自动回复配置响应DTO
 * @author IAMLZY
 * @date 2026/4/22
 */
@Data
public class RagAutoReplyConfigRespDTO {
    /** 回复延时秒数 */
    private Integer ragDelaySeconds;

    /** 账号级全局AI回复模板 */
    private String globalAiReplyTemplate;

    /** 账号级所有商品AI回复总开关 */
    private Boolean globalAiReplyEnabled;
}
