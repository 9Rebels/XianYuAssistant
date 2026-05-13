package com.feijimiao.xianyuassistant.controller.dto;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import lombok.Data;

@Data
public class RefreshAccountProfileRespDTO {
    private XianyuAccount account;
    private String message;
}
