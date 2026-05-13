package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 更新账号请求DTO
 */
@Data
public class UpdateAccountReqDTO {
    private Long accountId;       // 账号ID
    private String accountNote;   // 账号备注
    private String proxyType;     // 代理类型: http/https/socks5，null或空表示不使用代理
    private String proxyHost;     // 代理主机地址
    private Integer proxyPort;    // 代理端口
    private String proxyUsername; // 代理认证用户名
    private String proxyPassword; // 代理认证密码
    private Boolean updateProxy;  // 是否更新代理配置
    private String loginUsername; // 闲鱼登录用户名（手机号/邮箱）
    private String loginPassword; // 闲鱼登录密码
    private Boolean clearLoginPassword; // 是否清空已保存登录密码
}
