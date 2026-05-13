package com.feijimiao.xianyuassistant.config.rag;

import com.feijimiao.xianyuassistant.entity.XianyuAiProvider;
import com.feijimiao.xianyuassistant.mapper.XianyuAiProviderMapper;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(100)
public class AiProviderMigration implements ApplicationRunner {

    @Autowired
    private XianyuAiProviderMapper providerMapper;

    @Autowired
    private SysSettingService sysSettingService;

    @Override
    public void run(ApplicationArguments args) {
        Long count = providerMapper.selectCount(null);
        if (count != null && count > 0) {
            log.debug("[AiProviderMigration] ai_provider 表已有数据，跳过迁移");
            return;
        }

        log.info("[AiProviderMigration] 开始迁移旧 AI 配置到 ai_provider 表");
        String currentProvider = getSettingValue("ai_reply_provider");

        migrateAliyun(currentProvider);
        migrateOpenAiCompatible(currentProvider);

        // 确保至少有一个 provider 被激活
        XianyuAiProvider active = providerMapper.findActive();
        if (active == null) {
            XianyuAiProvider first = providerMapper.selectOne(null);
            if (first != null) {
                first.setIsActive(1);
                providerMapper.updateById(first);
            }
        }

        Long migrated = providerMapper.selectCount(null);
        log.info("[AiProviderMigration] 迁移完成，共迁移 {} 个提供商", migrated);
    }

    private void migrateAliyun(String currentProvider) {
        String apiKey = getSettingValue("ai_api_key");
        if (isBlank(apiKey)) return;

        XianyuAiProvider provider = new XianyuAiProvider();
        provider.setName("阿里百炼");
        provider.setApiKey(apiKey);
        provider.setBaseUrl(valueOrDefault(getSettingValue("ai_base_url"), "https://dashscope.aliyuncs.com/compatible-mode"));
        provider.setModel(valueOrDefault(getSettingValue("ai_model"), "deepseek-v3"));
        provider.setIsActive(isBlank(currentProvider) || "aliyun".equals(currentProvider) ? 1 : 0);
        provider.setEnabled(1);
        provider.setSortOrder(10);
        provider.setCreatedTime(LocalDateTime.now());
        provider.setUpdatedTime(LocalDateTime.now());
        providerMapper.insert(provider);
        log.info("[AiProviderMigration] 迁移阿里百炼配置, isActive={}", provider.getIsActive());
    }

    private void migrateOpenAiCompatible(String currentProvider) {
        String apiKey = getSettingValue("openai_compatible_api_key");
        if (isBlank(apiKey)) return;

        XianyuAiProvider provider = new XianyuAiProvider();
        provider.setName("通用 OpenAI 兼容");
        provider.setApiKey(apiKey);
        provider.setBaseUrl(valueOrDefault(getSettingValue("openai_compatible_base_url"), "https://api.openai.com/v1"));
        provider.setModel(valueOrDefault(getSettingValue("openai_compatible_model"), "gpt-4o-mini"));
        provider.setIsActive("openai_compatible".equals(currentProvider) ? 1 : 0);
        provider.setEnabled(1);
        provider.setSortOrder(20);
        provider.setCreatedTime(LocalDateTime.now());
        provider.setUpdatedTime(LocalDateTime.now());
        providerMapper.insert(provider);
        log.info("[AiProviderMigration] 迁移 OpenAI 兼容配置, isActive={}", provider.getIsActive());
    }

    private String getSettingValue(String key) {
        try {
            return sysSettingService.getSettingValue(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }
}
