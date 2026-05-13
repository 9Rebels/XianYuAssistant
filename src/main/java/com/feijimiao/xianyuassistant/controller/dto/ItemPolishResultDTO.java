package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ItemPolishResultDTO {
    private Boolean success;
    private Long xianyuAccountId;
    private Integer total;
    private Integer polished;
    private Integer failed;
    private String message;
    private Boolean recoveryAttempted;
    private Boolean needCaptcha;
    private Boolean needManual;
    private String manualVerifyUrl;
    private String captchaUrl;
    private String sessionId;
    private List<ItemPolishResultItemDTO> results = new ArrayList<>();

    @Data
    public static class ItemPolishResultItemDTO {
        private String xyGoodId;
        private String title;
        private Boolean success;
        private String error;
        private Boolean recoveryAttempted;
        private Boolean needCaptcha;
        private Boolean needManual;
        private String manualVerifyUrl;
        private String captchaUrl;
        private String sessionId;
    }
}
