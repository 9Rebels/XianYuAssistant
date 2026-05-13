package com.feijimiao.xianyuassistant.controller.dto;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import lombok.Data;

import java.util.List;

/**
 * 获取账号列表响应DTO
 */
@Data
public class GetAccountListRespDTO {
    private List<XianyuAccount> accounts;       // 账号列表
}