package com.feijimiao.xianyuassistant.entity;

import lombok.Data;

/**
 * 商品订单实体类
 */
@Data
public class XianyuGoodsOrder {
    
    private Long id;
    
    private Long xianyuAccountId;
    
    private Long xianyuGoodsId;
    
    private String xyGoodsId;
    
    private String pnmId;
    
    private String orderId;
    
    private String buyerUserId;
    
    private String buyerUserName;
    
    private String sid;
    
    private String content;

    private Integer deliveryMode;

    private String ruleName;

    private String deliverySnapshot;

    private String externalAllocationId;

    private Integer externalConfirmState;

    private Integer externalReturnState;

    private String externalReturnReason;

    private String triggerSource;

    private String triggerContent;
    
    private Integer state;
    
    private String failReason;
    
    private Integer confirmState;
    
    private String createTime;
    
    private String goodsTitle;
}
