package com.feijimiao.xianyuassistant.service.bo;

import lombok.Data;

import java.util.Map;

@Data
public class XianyuApiRecoveryRequest {
    private Long accountId;
    private String operationName;
    private String apiName;
    private Map<String, Object> dataMap;
    private String cookie;
    private String spmCnt;
    private String spmPre;
    private String version = "1.0";
}
