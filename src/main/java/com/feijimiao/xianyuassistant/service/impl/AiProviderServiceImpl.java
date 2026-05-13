package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.config.rag.DynamicAIChatClientManager;
import com.feijimiao.xianyuassistant.entity.XianyuAiProvider;
import com.feijimiao.xianyuassistant.mapper.XianyuAiProviderMapper;
import com.feijimiao.xianyuassistant.service.AiProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AiProviderServiceImpl implements AiProviderService {

    @Autowired
    private XianyuAiProviderMapper providerMapper;

    @Autowired
    @Lazy
    private DynamicAIChatClientManager dynamicAIChatClientManager;

    @Override
    public List<XianyuAiProvider> listAll() {
        return providerMapper.findAllEnabled();
    }

    @Override
    public XianyuAiProvider getById(Long id) {
        return providerMapper.selectById(id);
    }

    @Override
    public XianyuAiProvider getActiveProvider() {
        return providerMapper.findActive();
    }

    @Override
    public void save(XianyuAiProvider provider) {
        if (provider.getId() != null) {
            provider.setUpdatedTime(LocalDateTime.now());
            providerMapper.updateById(provider);
        } else {
            provider.setCreatedTime(LocalDateTime.now());
            provider.setUpdatedTime(LocalDateTime.now());
            if (provider.getEnabled() == null) {
                provider.setEnabled(1);
            }
            if (provider.getIsActive() == null) {
                provider.setIsActive(0);
            }
            if (provider.getSortOrder() == null) {
                provider.setSortOrder(100);
            }
            providerMapper.insert(provider);
        }
        triggerRebuild();
    }

    @Override
    public void deleteById(Long id) {
        providerMapper.deleteById(id);
        triggerRebuild();
    }

    @Override
    @Transactional
    public void activate(Long id) {
        XianyuAiProvider target = providerMapper.selectById(id);
        if (target == null) {
            throw new RuntimeException("提供商不存在");
        }
        providerMapper.deactivateAll();
        target.setIsActive(1);
        target.setUpdatedTime(LocalDateTime.now());
        providerMapper.updateById(target);
        triggerRebuild();
        log.info("[AiProvider] 切换激活提供商: id={}, name={}", id, target.getName());
    }

    private void triggerRebuild() {
        dynamicAIChatClientManager.forceRebuild();
    }
}
