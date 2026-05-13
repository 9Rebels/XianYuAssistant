package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class UpdateItemStockReqDTO {
    private Long xianyuAccountId;

    private String xyGoodsId;

    private Integer quantity;
}
