package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class PublishItemRespDTO {
    private Boolean success;
    private String message;
    private String itemId;
    private String itemUrl;
    private Long scheduledTaskId;
    private String scheduledTime;
    private Boolean recoveryAttempted;
    private Boolean needCaptcha;
    private Boolean needManual;
    private String manualVerifyUrl;
    private String captchaUrl;
    private String sessionId;
}
