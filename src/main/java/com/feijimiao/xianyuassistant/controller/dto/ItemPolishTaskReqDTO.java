package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class ItemPolishTaskReqDTO {
    private Long xianyuAccountId;
    private Integer enabled;
    private Integer runHour;
    private Integer randomDelayMaxMinutes;
}
