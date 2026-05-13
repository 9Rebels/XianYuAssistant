package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 获取所有商品请求DTO
 */
@Data
public class AllItemsReqDTO {
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 每页数量，默认20
     */
    private Integer pageSize = 20;
    
    /**
     * 最大页数限制
     */
    private Integer maxPages;

    /**
     * 同步商品状态：0=在售，2=已售出。已下架暂不支持同步。
     */
    private Integer syncStatus = 0;
}
