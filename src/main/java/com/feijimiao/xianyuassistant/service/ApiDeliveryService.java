package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryResult;

/**
 * 通用API发货服务。
 */
public interface ApiDeliveryService {

    ApiDeliveryResult allocate(XianyuGoodsAutoDeliveryConfig config, ApiDeliveryContext context);

    ApiDeliveryResult confirm(XianyuGoodsAutoDeliveryConfig config, ApiDeliveryContext context);

    ApiDeliveryResult returnAllocation(XianyuGoodsAutoDeliveryConfig config, ApiDeliveryContext context);
}
