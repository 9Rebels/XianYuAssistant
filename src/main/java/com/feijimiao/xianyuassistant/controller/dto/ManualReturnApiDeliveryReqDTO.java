package com.feijimiao.xianyuassistant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动回库 API 发货记录请求
 */
@Data
public class ManualReturnApiDeliveryReqDTO {

    @NotNull(message = "闲鱼账号ID不能为空")
    private Long xianyuAccountId;

    @NotNull(message = "发货记录ID不能为空")
    private Long recordId;

    @NotBlank(message = "商品ID不能为空")
    private String xyGoodsId;
}
