package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;

public interface XianyuApiRecoveryService {
    XianyuApiRecoveryResult callApi(XianyuApiRecoveryRequest request);
}
