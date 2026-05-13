package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PublishItemAddrDTO {
    private String prov;
    private String city;
    private String area;
    private Integer divisionId;
    private String gps;
    private String poiId;
    private String poiName;
}
