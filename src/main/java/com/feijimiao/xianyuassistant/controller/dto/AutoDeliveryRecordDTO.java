package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 自动发货记录DTO (简化版)
 */
@Data
public class AutoDeliveryRecordDTO {
    
    /**
     * 主键ID
     */
    private Long id;
    
    private Long xianyuAccountId;
    
    private String xyGoodsId;
    
    /**
     * 商品标题
     */
    private String goodsTitle;
    
    /**
     * 买家用户名称
     */
    private String buyerUserName;
    
    /**
     * 发货消息内容
     */
    private String content;

    /**
     * 发货模式快照
     */
    private Integer deliveryMode;

    /**
     * 发货规则名称快照
     */
    private String ruleName;

    /**
     * 触发来源：payment/bargain/manual
     */
    private String triggerSource;

    /**
     * 触发消息文案
     */
    private String triggerContent;
    
    /**
     * 发货是否成功: 1-成功, 0-失败
     */
    private Integer state;

    /**
     * 失败原因
     */
    private String failReason;
    
    /**
     * 确认发货状态: 0-未确认, 1-已确认
     */
    private Integer confirmState;
    
    /**
     * 订单ID
     */
    private String orderId;

    /**
     * API发货外部占用ID
     */
    private String externalAllocationId;

    /**
     * API发货确认状态：0-未确认，1-已确认，-1-确认失败
     */
    private Integer externalConfirmState;

    /**
     * API发货回库状态：0-未回库，1-已回库，-1-回库失败
     */
    private Integer externalReturnState;

    /**
     * API发货回库原因或失败原因
     */
    private String externalReturnReason;
    
    /**
     * 创建时间
     */
    private String createTime;
}
