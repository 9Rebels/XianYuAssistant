package com.feijimiao.xianyuassistant.service.bo;

import lombok.Data;

/**
 * 登录设备信息。
 */
@Data
public class LoginDeviceBO {
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
