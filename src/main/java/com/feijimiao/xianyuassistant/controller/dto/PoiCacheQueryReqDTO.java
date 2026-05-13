package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PoiCacheQueryReqDTO {
    private Long xianyuAccountId;
    private Integer divisionId;
    private String keyword;
}
