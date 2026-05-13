package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryConfigQueryReqDTO;
import com.feijimiao.xianyuassistant.service.AutoDeliveryConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 自动发货配置服务实现
 */
@Slf4j
@Service
public class AutoDeliveryConfigServiceImpl implements AutoDeliveryConfigService {

    private static final int MAX_DELIVERY_DELAY_SECONDS = 86400;
    
    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    
    @Override
    public ResultObject<AutoDeliveryConfigRespDTO> saveOrUpdateConfig(AutoDeliveryConfigReqDTO reqDTO) {
        try {
            XianyuGoodsAutoDeliveryConfig existingConfig = null;
            if (reqDTO.getId() != null) {
                existingConfig = autoDeliveryConfigMapper.selectById(reqDTO.getId());
                if (existingConfig == null) {
                    return ResultObject.failed("未找到要更新的自动发货规则");
                }
            }
            
            XianyuGoodsAutoDeliveryConfig config;
            if (existingConfig != null) {
                // 更新现有配置
                config = existingConfig;
                config.setDeliveryMode(reqDTO.getDeliveryMode());
                config.setRuleName(reqDTO.getRuleName());
                config.setMatchKeyword(reqDTO.getMatchKeyword());
                config.setMatchType(reqDTO.getMatchType());
                config.setPriority(reqDTO.getPriority());
                config.setEnabled(reqDTO.getEnabled());
                config.setStock(reqDTO.getStock());
                config.setStockWarnThreshold(reqDTO.getStockWarnThreshold());
                config.setAutoDeliveryContent(reqDTO.getAutoDeliveryContent());
                config.setKamiConfigIds(reqDTO.getKamiConfigIds());
                config.setKamiDeliveryTemplate(reqDTO.getKamiDeliveryTemplate());
                config.setAutoDeliveryImageUrl(reqDTO.getAutoDeliveryImageUrl());
                config.setPostDeliveryText(reqDTO.getPostDeliveryText());
                config.setXianyuGoodsId(reqDTO.getXianyuGoodsId());
                config.setAutoConfirmShipment(reqDTO.getAutoConfirmShipment());
                config.setDeliveryDelaySeconds(reqDTO.getDeliveryDelaySeconds());
                config.setTriggerPaymentEnabled(reqDTO.getTriggerPaymentEnabled());
                config.setTriggerBargainEnabled(reqDTO.getTriggerBargainEnabled());
                config.setApiAllocateUrl(reqDTO.getApiAllocateUrl());
                config.setApiConfirmUrl(reqDTO.getApiConfirmUrl());
                config.setApiReturnUrl(reqDTO.getApiReturnUrl());
                config.setApiHeaderValue(reqDTO.getApiHeaderValue());
                config.setApiRequestExtras(reqDTO.getApiRequestExtras());
                config.setApiDeliveryTemplate(reqDTO.getApiDeliveryTemplate());
                fillConfigDefaults(config);
                String validationError = validateConfig(config);
                if (validationError != null) {
                    return ResultObject.failed(validationError);
                }
                
                autoDeliveryConfigMapper.updateById(config);
                log.info("更新自动发货配置成功，ID: {}", config.getId());
            } else {
                // 创建新配置
                config = new XianyuGoodsAutoDeliveryConfig();
                BeanUtils.copyProperties(reqDTO, config);
                fillConfigDefaults(config);
                String validationError = validateConfig(config);
                if (validationError != null) {
                    return ResultObject.failed(validationError);
                }
                
                autoDeliveryConfigMapper.insert(config);
                log.info("创建自动发货配置成功，ID: {}", config.getId());
            }
            
            AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
            BeanUtils.copyProperties(config, respDTO);
            fillRuleCount(respDTO);
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("保存自动发货配置失败", e);
            return ResultObject.failed("保存自动发货配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<AutoDeliveryConfigRespDTO> getConfig(AutoDeliveryConfigQueryReqDTO reqDTO) {
        try {
            log.info("开始查询自动发货配置: xianyuAccountId={}, xyGoodsId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
            
            XianyuGoodsAutoDeliveryConfig config;
            
            if (reqDTO.getXyGoodsId() != null && !reqDTO.getXyGoodsId().trim().isEmpty()) {
                // 根据账号ID和商品ID查询
                config = autoDeliveryConfigMapper.findByAccountIdAndGoodsId(
                        reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
                log.info("根据账号ID和商品ID查询结果: {}", config != null ? "找到配置" : "未找到配置");
            } else {
                // 只根据账号ID查询第一个配置（用于页面初始化）
                List<XianyuGoodsAutoDeliveryConfig> configs = autoDeliveryConfigMapper
                        .findByAccountId(reqDTO.getXianyuAccountId());
                config = configs.isEmpty() ? null : configs.get(0);
                log.info("根据账号ID查询结果: 找到{}条配置", configs.size());
            }
            
            if (config == null) {
                log.info("未找到匹配的配置，返回null");
                return ResultObject.success(null);
            }
            
            // 检查时间字段是否为null
            if (config.getCreateTime() == null) {
                log.warn("配置记录的创建时间为空: id={}", config.getId());
            }
            if (config.getUpdateTime() == null) {
                log.warn("配置记录的更新时间为空: id={}", config.getId());
            }
            
            AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
            BeanUtils.copyProperties(config, respDTO);
            fillRuleCount(respDTO);
            
            log.info("查询自动发货配置成功: id={}, deliveryMode={}, hasContent={}", 
                    respDTO.getId(), respDTO.getDeliveryMode(), 
                    respDTO.getAutoDeliveryContent() != null && !respDTO.getAutoDeliveryContent().isEmpty());
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("查询自动发货配置失败", e);
            return ResultObject.failed("查询自动发货配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<List<AutoDeliveryConfigRespDTO>> getConfigsByAccountId(Long xianyuAccountId) {
        try {
            List<XianyuGoodsAutoDeliveryConfig> configs = autoDeliveryConfigMapper
                    .findByAccountId(xianyuAccountId);
            
            List<AutoDeliveryConfigRespDTO> respDTOs = configs.stream()
                    .map(config -> {
                        AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
                        BeanUtils.copyProperties(config, respDTO);
                        fillRuleCount(respDTO);
                        return respDTO;
                    })
                    .collect(Collectors.toList());
            
            return ResultObject.success(respDTOs);
        } catch (Exception e) {
            log.error("查询账号自动发货配置列表失败", e);
            return ResultObject.failed("查询账号自动发货配置列表失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<List<AutoDeliveryConfigRespDTO>> getConfigsByGoodsId(Long xianyuAccountId, String xyGoodsId) {
        try {
            List<XianyuGoodsAutoDeliveryConfig> configs = autoDeliveryConfigMapper
                    .findRulesByAccountIdAndGoodsId(xianyuAccountId, xyGoodsId);
            int ruleCount = configs.size();
            List<AutoDeliveryConfigRespDTO> respDTOs = configs.stream()
                    .map(config -> {
                        AutoDeliveryConfigRespDTO respDTO = new AutoDeliveryConfigRespDTO();
                        BeanUtils.copyProperties(config, respDTO);
                        respDTO.setRuleCount(ruleCount);
                        return respDTO;
                    })
                    .collect(Collectors.toList());
            return ResultObject.success(respDTOs);
        } catch (Exception e) {
            log.error("查询商品自动发货规则列表失败", e);
            return ResultObject.failed("查询商品自动发货规则列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<Void> deleteConfig(Long xianyuAccountId, String xyGoodsId) {
        try {
            LambdaQueryWrapper<XianyuGoodsAutoDeliveryConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(XianyuGoodsAutoDeliveryConfig::getXianyuAccountId, xianyuAccountId)
                   .eq(XianyuGoodsAutoDeliveryConfig::getXyGoodsId, xyGoodsId);
            
            int deletedCount = autoDeliveryConfigMapper.delete(wrapper);
            
            if (deletedCount > 0) {
                log.info("删除自动发货配置成功，账号ID: {}, 商品ID: {}", xianyuAccountId, xyGoodsId);
                return ResultObject.success(null);
            } else {
                return ResultObject.failed("未找到对应的自动发货配置");
            }
        } catch (Exception e) {
            log.error("删除自动发货配置失败", e);
            return ResultObject.failed("删除自动发货配置失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<Void> deleteConfigById(Long id) {
        try {
            int deletedCount = autoDeliveryConfigMapper.deleteById(id);
            if (deletedCount > 0) {
                return ResultObject.success(null);
            }
            return ResultObject.failed("未找到对应的自动发货规则");
        } catch (Exception e) {
            log.error("删除自动发货规则失败", e);
            return ResultObject.failed("删除自动发货规则失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<Void> updateEnabled(Long id, Integer enabled) {
        try {
            autoDeliveryConfigMapper.updateEnabled(id, normalizeEnabled(enabled));
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新自动发货规则启用状态失败", e);
            return ResultObject.failed("更新自动发货规则启用状态失败: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<Void> updateStock(Long id, Integer stock) {
        try {
            autoDeliveryConfigMapper.updateStock(id, stock != null ? stock : -1);
            return ResultObject.success(null);
        } catch (Exception e) {
            log.error("更新自动发货库存失败", e);
            return ResultObject.failed("更新自动发货库存失败: " + e.getMessage());
        }
    }

    private void fillConfigDefaults(XianyuGoodsAutoDeliveryConfig config) {
        if (config.getDeliveryMode() == null) config.setDeliveryMode(1);
        if (config.getMatchType() == null) config.setMatchType(1);
        if (config.getPriority() == null) config.setPriority(100);
        if (config.getEnabled() == null) config.setEnabled(1);
        if (config.getStock() == null) config.setStock(-1);
        if (config.getStockWarnThreshold() == null) config.setStockWarnThreshold(0);
        if (config.getTotalDelivered() == null) config.setTotalDelivered(0);
        if (config.getTodayDelivered() == null) config.setTodayDelivered(0);
        if (config.getDeliveryDelaySeconds() == null || config.getDeliveryDelaySeconds() < 0) {
            config.setDeliveryDelaySeconds(0);
        }
        if (config.getDeliveryDelaySeconds() > MAX_DELIVERY_DELAY_SECONDS) {
            config.setDeliveryDelaySeconds(MAX_DELIVERY_DELAY_SECONDS);
        }
        if (config.getTriggerPaymentEnabled() == null) config.setTriggerPaymentEnabled(1);
        if (config.getTriggerBargainEnabled() == null) config.setTriggerBargainEnabled(0);
    }

    private String validateConfig(XianyuGoodsAutoDeliveryConfig config) {
        if (config.getDeliveryMode() != null && config.getDeliveryMode() == 4 && isBlank(config.getApiAllocateUrl())) {
            return "API发货模式必须配置分配接口URL";
        }
        if (config.getDeliveryMode() != null && config.getDeliveryMode() == 4) {
            String extras = config.getApiRequestExtras();
            if (!isBlank(extras)) {
                try {
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(extras);
                } catch (Exception e) {
                    return "API附加请求JSON格式不正确";
                }
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Integer normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }

    private void fillRuleCount(AutoDeliveryConfigRespDTO respDTO) {
        if (respDTO == null || respDTO.getXianyuAccountId() == null || respDTO.getXyGoodsId() == null) {
            return;
        }
        respDTO.setRuleCount(autoDeliveryConfigMapper
                .findRulesByAccountIdAndGoodsId(respDTO.getXianyuAccountId(), respDTO.getXyGoodsId())
                .size());
    }
}
