package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 自动发货配置请求DTO
 */
@Data
public class AutoDeliveryConfigReqDTO {

    /**
     * 配置ID。传入时更新指定规则，不传时创建新规则。
     */
    private Long id;
    
    /**
     * 闲鱼账号ID（必选）
     */
    @NotNull(message = "闲鱼账号ID不能为空")
    private Long xianyuAccountId;
    
    /**
     * 本地闲鱼商品ID
     */
    private Long xianyuGoodsId;
    
    /**
     * 闲鱼的商品ID（必选）
     */
    @NotNull(message = "闲鱼商品ID不能为空")
    private String xyGoodsId;
    
    /**
     * 发货模式：1-文本发货，2-卡密发货，3-自定义发货，4-API发货
     */
    private Integer deliveryMode = 1;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 本地匹配关键词，多个用逗号分隔
     */
    private String matchKeyword;

    /**
     * 匹配方式：1-任意关键词，2-全部关键词
     */
    private Integer matchType = 1;

    /**
     * 规则优先级，数字越小越靠前
     */
    private Integer priority = 100;

    /**
     * 规则启用状态：0-关闭，1-开启
     */
    private Integer enabled = 1;

    /**
     * 本地库存：-1表示不限库存
     */
    private Integer stock = -1;

    /**
     * 库存预警阈值：0表示不预警
     */
    private Integer stockWarnThreshold = 0;

    /**
     * 自动发货的文本内容
     */
    private String autoDeliveryContent;

    /**
     * 卡密发货：绑定的卡密配置ID列表（逗号分隔）
     */
    private String kamiConfigIds;

    /**
     * 卡密发货文案模板，使用{kmKey}占位符替换卡密内容
     */
    private String kamiDeliveryTemplate;

    /**
     * 自动发货图片URL
     */
    private String autoDeliveryImageUrl;

    /**
     * 发货内容发送成功后追加发送的文本
     */
    private String postDeliveryText;
    
    /**
     * 自动确认发货开关：0-关闭，1-开启
     */
    private Integer autoConfirmShipment;

    /**
     * 延时发货秒数：0表示立即发货
     */
    private Integer deliveryDelaySeconds = 0;

    /**
     * 付款消息触发：0-关闭，1-开启
     */
    private Integer triggerPaymentEnabled = 1;

    /**
     * 小刀/讲价卡片触发：0-关闭，1-开启
     */
    private Integer triggerBargainEnabled = 0;

    /**
     * API发货：分配/占用接口URL
     */
    private String apiAllocateUrl;

    /**
     * API发货：确认交付接口URL（可选）
     */
    private String apiConfirmUrl;

    /**
     * API发货：回库接口URL（可选）
     */
    private String apiReturnUrl;

    /**
     * API发货：密钥请求头值
     */
    private String apiHeaderValue;

    /**
     * API发货：附加自定义请求JSON
     */
    private String apiRequestExtras;

    /**
     * API发货：发送文案模板，支持 {apiContent}
     */
    private String apiDeliveryTemplate;
}
