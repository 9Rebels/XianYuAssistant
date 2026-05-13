package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 从数据库获取商品列表请求DTO
 */
@Data
public class ItemListFromDbReqDTO {
    
    /**
     * 商品状态（0=在售, 1=已下架, 2=已售出）
     * 为空时查询全部状态
     */
    private Integer status;
    
    /**
     * 闲鱼账号ID（可选）
     */
    private Long xianyuAccountId;
    
    /**
     * 页码，从1开始
     * 默认1
     */
    private Integer pageNum = 1;
    
    /**
     * 每页数量
     * 默认20
     */
    private Integer pageSize = 20;

    /**
     * 搜索关键词，支持商品标题和闲鱼商品ID
     */
    private String keyword;
}
