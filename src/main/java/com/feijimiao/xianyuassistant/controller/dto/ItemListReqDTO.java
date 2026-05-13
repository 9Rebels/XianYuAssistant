package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 获取商品列表请求DTO
 */
@Data
public class ItemListReqDTO {
    /**
     * 账号ID
     */
    private String cookieId;
    
    /**
     * 页码，默认1
     */
    private Integer pageNumber = 1;
    
    /**
     * 每页数量，默认20
     */
    private Integer pageSize = 20;

    /**
     * 闲鱼商品分组名称，例如：在售、已售出
     */
    private String groupName = "在售";

    /**
     * 闲鱼商品分组ID
     */
    private String groupId = "58877261";

    /**
     * 是否默认分组
     */
    private Boolean defaultGroup = true;

    /**
     * 是否需要返回分组信息
     */
    private Boolean needGroupInfo = false;

    /**
     * 本地同步状态：0=在售，2=已售出
     */
    private Integer syncStatus = 0;
}
