package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PoiFetchReqDTO {
    private Long xianyuAccountId;
    private Integer divisionId;
    private String prov;
    private String city;
    private String area;
    private String longitude;
    private String latitude;
}
