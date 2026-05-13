package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PoiCandidateDTO {
    private String prov;
    private String city;
    private String area;
    private Integer divisionId;
    private String poiId;
    private String poiName;
    private String gps;
    private String latitude;
    private String longitude;
    private String address;
}
