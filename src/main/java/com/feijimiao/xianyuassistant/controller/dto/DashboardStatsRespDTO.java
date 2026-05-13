package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 首页统计信息响应DTO
 */
@Data
public class DashboardStatsRespDTO {
    
    /**
     * 账号总数
     */
    private Integer accountCount;
    
    /**
     * 商品总数
     */
    private Integer itemCount;
    
    /**
     * 在售商品数
     */
    private Integer sellingItemCount;
    
    /**
     * 已下架商品数
     */
    private Integer offShelfItemCount;
    
    /**
     * 已售出商品数
     */
    private Integer soldItemCount;

    /**
     * 自动发货成功数
     */
    private Integer deliverySuccessCount;

    /**
     * 自动发货失败数
     */
    private Integer deliveryFailCount;

    /**
     * 待发货数
     */
    private Integer pendingDeliveryCount;
}
