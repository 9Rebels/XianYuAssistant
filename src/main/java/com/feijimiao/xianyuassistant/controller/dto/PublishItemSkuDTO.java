package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PublishItemSkuDTO {
    private String propertyKey;
    private String propertyValue;
    private String secondPropertyKey;
    private String secondPropertyValue;
    private String price;
    private String quantity;
}
