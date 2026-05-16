package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 登录设备展示DTO。
 */
@Data
public class LoginDeviceDTO {
    private Long id;
    private String deviceName;
    private String browserName;
    private String osName;
    private String loginIp;
    private String loginTime;
    private String lastActiveTime;
    private String expireTime;
    private Integer status;
    private Boolean current;
}
