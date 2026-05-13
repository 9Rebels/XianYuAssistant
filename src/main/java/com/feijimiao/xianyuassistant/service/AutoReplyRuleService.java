package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleBatchImportReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleQueryReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoReplyRuleRespDTO;
import com.feijimiao.xianyuassistant.service.bo.AutoReplyRuleMatch;

import java.util.List;

/**
 * 自动回复规则服务
 */
public interface AutoReplyRuleService {

    ResultObject<List<AutoReplyRuleRespDTO>> list(AutoReplyRuleQueryReqDTO reqDTO);

    ResultObject<AutoReplyRuleRespDTO> save(AutoReplyRuleReqDTO reqDTO);

    ResultObject<Integer> batchImport(AutoReplyRuleBatchImportReqDTO reqDTO);

    ResultObject<Void> delete(Long id);

    AutoReplyRuleMatch match(Long accountId, String xyGoodsId, String buyerMessage);
}
