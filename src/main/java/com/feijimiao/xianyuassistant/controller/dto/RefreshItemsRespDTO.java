package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class RefreshItemsRespDTO {
    private Boolean success;
    private Integer totalCount;
    private Integer successCount;
    private Integer removedCount;
    private List<String> updatedItemIds;
    private String message;
    private String syncId;
    private Integer syncStatus;
    private String syncLabel;
    private Boolean recoveryAttempted;
    private Boolean needCaptcha;
    private Boolean needManual;
    private String manualVerifyUrl;
    private String captchaUrl;
    private String sessionId;
}
