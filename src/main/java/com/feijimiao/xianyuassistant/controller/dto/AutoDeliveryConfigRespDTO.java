package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动发货配置响应DTO
 */
@Data
public class AutoDeliveryConfigRespDTO {

    /**
     * 当前商品的规则数量
     */
    private Integer ruleCount;
    
    /**
     * 配置ID
     */
    private Long id;
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 本地闲鱼商品ID
     */
    private Long xianyuGoodsId;
    
    /**
     * 闲鱼的商品ID
     */
    private String xyGoodsId;
    
    /**
     * 发货模式：1-文本发货，2-卡密发货，3-自定义发货，4-API发货
     */
    private Integer deliveryMode;

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
    private Integer matchType;

    /**
     * 规则优先级，数字越小越靠前
     */
    private Integer priority;

    /**
     * 规则启用状态：0-关闭，1-开启
     */
    private Integer enabled;

    /**
     * 本地库存：-1表示不限库存
     */
    private Integer stock;

    /**
     * 库存预警阈值：0表示不预警
     */
    private Integer stockWarnThreshold;

    /**
     * 累计成功发货次数
     */
    private Integer totalDelivered;

    /**
     * 今日成功发货次数
     */
    private Integer todayDelivered;

    /**
     * 最近一次成功发货时间
     */
    private LocalDateTime lastDeliveryTime;

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
    private Integer deliveryDelaySeconds;

    /**
     * 付款消息触发：0-关闭，1-开启
     */
    private Integer triggerPaymentEnabled;

    /**
     * 小刀/讲价卡片触发：0-关闭，1-开启
     */
    private Integer triggerBargainEnabled;

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
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
