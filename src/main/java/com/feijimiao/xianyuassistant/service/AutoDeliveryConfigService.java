package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigQueryReqDTO;

import java.util.List;

/**
 * 自动发货配置服务接口
 */
public interface AutoDeliveryConfigService {
    
    /**
     * 保存或更新自动发货配置
     *
     * @param reqDTO 配置请求DTO
     * @return 操作结果
     */
    ResultObject<AutoDeliveryConfigRespDTO> saveOrUpdateConfig(AutoDeliveryConfigReqDTO reqDTO);
    
    /**
     * 根据账号ID和商品ID查询配置
     *
     * @param reqDTO 查询请求DTO
     * @return 配置信息
     */
    ResultObject<AutoDeliveryConfigRespDTO> getConfig(AutoDeliveryConfigQueryReqDTO reqDTO);

    /**
     * 根据账号ID和商品ID查询所有规则
     */
    ResultObject<List<AutoDeliveryConfigRespDTO>> getConfigsByGoodsId(Long xianyuAccountId, String xyGoodsId);
    
    /**
     * 根据账号ID查询所有配置
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @return 配置列表
     */
    ResultObject<List<AutoDeliveryConfigRespDTO>> getConfigsByAccountId(Long xianyuAccountId);
    
    /**
     * 删除配置
     *
     * @param xianyuAccountId 闲鱼账号ID
     * @param xyGoodsId 闲鱼商品ID
     * @return 操作结果
     */
    ResultObject<Void> deleteConfig(Long xianyuAccountId, String xyGoodsId);

    /**
     * 删除单条规则
     */
    ResultObject<Void> deleteConfigById(Long id);

    /**
     * 更新规则启用状态
     */
    ResultObject<Void> updateEnabled(Long id, Integer enabled);

    /**
     * 更新本地库存
     */
    ResultObject<Void> updateStock(Long id, Integer stock);
}
