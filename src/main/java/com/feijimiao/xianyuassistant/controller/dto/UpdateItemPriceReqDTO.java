package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 修改商品价格请求DTO
 */
@Data
public class UpdateItemPriceReqDTO {
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;

    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;

    /**
     * 修改后的价格，单位元
     */
    private String price;
}
