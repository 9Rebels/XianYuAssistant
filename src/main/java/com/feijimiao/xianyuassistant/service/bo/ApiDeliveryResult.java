package com.feijimiao.xianyuassistant.service.bo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * API发货调用结果。
 */
@Data
@AllArgsConstructor(staticName = "of")
public class ApiDeliveryResult {

    private boolean success;
    private String content;
    private String allocationId;
    private String message;
}
