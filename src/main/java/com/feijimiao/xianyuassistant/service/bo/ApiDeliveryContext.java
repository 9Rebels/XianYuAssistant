package com.feijimiao.xianyuassistant.service.bo;

import lombok.Builder;
import lombok.Data;

/**
 * API发货请求上下文。
 */
@Data
@Builder
public class ApiDeliveryContext {

    private Long recordId;
    private Long accountId;
    private Long xianyuGoodsId;
    private String xyGoodsId;
    private String sId;
    private String orderId;
    private String buyerUserId;
    private String buyerUserName;
    private String triggerSource;
    private String triggerContent;
    private Integer buyQuantity;
    private Integer deliveryIndex;
    private Integer deliveryTotal;
    private Long ruleId;
    private String ruleName;
    private String allocationId;
    private String reason;
    private String apiRequestExtras;
}
