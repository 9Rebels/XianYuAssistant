package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 获取自动发货记录请求DTO
 */
@Data
public class AutoDeliveryRecordReqDTO {
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 商品ID（可选，不传则查询所有商品）
     */
    private String xyGoodsId;

    /**
     * 发货状态：1-成功，0-待发货，-1-失败；为空则全部
     */
    private Integer state;
    
    /**
     * 页码
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量
     */
    private Integer pageSize = 20;
}
