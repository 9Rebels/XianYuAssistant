package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PoiCacheSaveReqDTO {
    private Long xianyuAccountId;
    private Integer divisionId;
    private String prov;
    private String city;
    private String area;
    private String poiId;
    private String poiName;
    private String gps;
    private String latitude;
    private String longitude;
    private String address;
    private Boolean defaultPoi;
}
