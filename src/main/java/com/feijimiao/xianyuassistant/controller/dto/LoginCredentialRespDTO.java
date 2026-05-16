package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 登录凭据响应DTO。
 */
@Data
public class LoginCredentialRespDTO {
    private Long accountId;
    private String loginUsername;
    private String loginPassword;
}
