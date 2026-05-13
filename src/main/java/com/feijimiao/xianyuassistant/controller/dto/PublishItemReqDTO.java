package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class PublishItemReqDTO {
    private Long xianyuAccountId;
    private String title;
    private String desc;
    private String price;
    private String origPrice;
    private String quantity;
    private Boolean freeShipping;
    private String shippingType;
    private Boolean supportSelfPick;
    private String postFee;
    private Boolean scheduled;
    private String scheduledTime;
    private List<String> imageUrls;
    private PublishItemCatDTO itemCat;
    private PublishItemAddrDTO itemAddr;
    private List<PublishItemLabelDTO> itemLabels;
    private List<PublishItemSpecDTO> itemProperties;
    private List<PublishItemSkuDTO> itemSkuList;
}
