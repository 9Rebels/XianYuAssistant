package com.feijimiao.xianyuassistant.config.rag;

import com.feijimiao.xianyuassistant.entity.XianyuAiProvider;
import com.feijimiao.xianyuassistant.service.AiProviderService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class DynamicAIChatClientManager {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_MODEL = "deepseek-v3";

    // 旧 key-value 配置 key（向后兼容 fallback 用）
    private static final String AI_API_KEY_SETTING = "ai_api_key";
    private static final String AI_BASE_URL_SETTING = "ai_base_url";
    private static final String AI_MODEL_SETTING = "ai_model";
    private static final String OPENAI_COMPATIBLE_API_KEY_SETTING = "openai_compatible_api_key";
    private static final String OPENAI_COMPATIBLE_BASE_URL_SETTING = "openai_compatible_base_url";
    private static final String OPENAI_COMPATIBLE_MODEL_SETTING = "openai_compatible_model";
    private static final String AI_REPLY_PROVIDER_SETTING = "ai_reply_provider";

    @Autowired
    @Lazy
    private AiProviderService aiProviderService;

    @Autowired
    @Lazy
    private SysSettingService sysSettingService;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    /** 当前缓存的API Key，用于判断是否需要重建 */
    private volatile String cachedApiKey;

    /** 当前缓存的Base URL */
    private volatile String cachedBaseUrl;

    /** 当前缓存的Model */
    private volatile String cachedModel;

    /** 当前缓存的服务提供方 */
    private volatile String cachedProvider;

    /** 当前ChatClient实例 */
    private volatile ChatClient chatClient;

    /** 读写锁，保护ChatClient的线程安全 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 获取ChatClient实例
     */
    public ChatClient getChatClient() {
        if (!aiEnabled) {
            log.debug("[DynamicAI] AI功能未启用(ai.enabled=false)");
            return null;
        }

        AIProviderConfig currentConfig = resolveActiveProviderConfig();
        String currentApiKey = currentConfig.getApiKey();
        String currentBaseUrl = currentConfig.getBaseUrl();
        String currentModel = currentConfig.getModel();
        String currentProvider = currentConfig.getProvider();

        if (currentApiKey == null || currentApiKey.trim().isEmpty()) {
            log.debug("[DynamicAI] API Key未配置，AI功能不可用");
            return null;
        }

        boolean needRebuild = chatClient == null
                || !currentApiKey.equals(cachedApiKey)
                || !safeEquals(currentBaseUrl, cachedBaseUrl)
                || !safeEquals(currentModel, cachedModel)
                || !safeEquals(currentProvider, cachedProvider);

        if (needRebuild) {
            lock.writeLock().lock();
            try {
                boolean stillNeedRebuild = chatClient == null
                        || !currentApiKey.equals(cachedApiKey)
                        || !safeEquals(currentBaseUrl, cachedBaseUrl)
                        || !safeEquals(currentModel, cachedModel)
                        || !safeEquals(currentProvider, cachedProvider);

                if (stillNeedRebuild) {
                    log.info("[DynamicAI] 检测到AI配置变化，重建ChatClient: provider={}, baseUrl={}, model={}, apiKey={}***{}",
                            currentProvider, currentBaseUrl, currentModel,
                            currentApiKey.substring(0, Math.min(4, currentApiKey.length())),
                            currentApiKey.length() > 8 ? currentApiKey.substring(currentApiKey.length() - 4) : "****");

                    chatClient = buildChatClient(currentApiKey, currentBaseUrl, currentModel);
                    cachedApiKey = currentApiKey;
                    cachedBaseUrl = currentBaseUrl;
                    cachedModel = currentModel;
                    cachedProvider = currentProvider;

                    log.info("[DynamicAI] ChatClient重建完成");
                }
            } catch (Exception e) {
                log.error("[DynamicAI] ChatClient重建失败", e);
                chatClient = null;
                cachedApiKey = null;
            } finally {
                lock.writeLock().unlock();
            }
        }

        lock.readLock().lock();
        try {
            return chatClient;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isAvailable() {
        if (!aiEnabled) {
            return false;
        }
        return !isBlank(resolveActiveProviderConfig().getApiKey());
    }

    public AIStatusInfo getStatusInfo() {
        AIStatusInfo info = new AIStatusInfo();
        info.setEnabled(aiEnabled);

        if (!aiEnabled) {
            info.setAvailable(false);
            info.setMessage("AI功能未启用(ai.enabled=false)");
            return info;
        }

        AIProviderConfig activeConfig = resolveActiveProviderConfig();
        List<XianyuAiProvider> allProviders = aiProviderService.listAll();
        long configuredCount = allProviders.stream()
                .filter(p -> !isBlank(p.getApiKey()))
                .count();

        info.setProvider(activeConfig.getProvider());
        info.setProviderName(activeConfig.getProviderName());
        info.setBaseUrl(activeConfig.getBaseUrl());
        info.setModel(activeConfig.getModel());
        info.setConfiguredCount((int) configuredCount);

        if (isBlank(activeConfig.getApiKey())) {
            info.setAvailable(false);
            info.setMessage("API Key未配置，请在系统设置中配置AI服务");
        } else {
            info.setAvailable(true);
            info.setApiKeyConfigured(true);
            info.setMessage("AI服务可用，当前使用" + activeConfig.getProviderName());
        }

        return info;
    }

    /**
     * 强制重建ChatClient（配置变更时调用）
     */
    public void forceRebuild() {
        log.info("[DynamicAI] 收到强制重建信号，清除缓存");
        lock.writeLock().lock();
        try {
            cachedApiKey = null;
            cachedBaseUrl = null;
            cachedModel = null;
            cachedProvider = null;
            chatClient = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public AIProviderConfig resolveActiveProviderConfig() {
        // 优先从 xianyu_ai_provider 表读取激活的提供商
        try {
            XianyuAiProvider active = aiProviderService.getActiveProvider();
            if (active != null && !isBlank(active.getApiKey())) {
                return new AIProviderConfig(
                        String.valueOf(active.getId()),
                        active.getName(),
                        active.getApiKey(),
                        normalizeOpenAiBaseUrl(active.getBaseUrl(), DEFAULT_BASE_URL),
                        valueOrDefault(active.getModel(), DEFAULT_MODEL)
                );
            }
            // 新表中没有激活的 provider，尝试找第一个有 apiKey 的
            List<XianyuAiProvider> all = aiProviderService.listAll();
            for (XianyuAiProvider p : all) {
                if (!isBlank(p.getApiKey())) {
                    return new AIProviderConfig(
                            String.valueOf(p.getId()),
                            p.getName(),
                            p.getApiKey(),
                            normalizeOpenAiBaseUrl(p.getBaseUrl(), DEFAULT_BASE_URL),
                            valueOrDefault(p.getModel(), DEFAULT_MODEL)
                    );
                }
            }
        } catch (Exception e) {
            log.debug("[DynamicAI] 从 ai_provider 表读取失败，尝试 fallback: {}", e.getMessage());
        }

        // Fallback: 从旧 sys_setting key-value 读取（向后兼容）
        return resolveActiveProviderConfigLegacy();
    }

    private AIProviderConfig resolveActiveProviderConfigLegacy() {
        String selectedProvider = valueOrDefault(getSettingValue(AI_REPLY_PROVIDER_SETTING), "aliyun");
        if ("openai_compatible".equals(selectedProvider)) {
            String key = getSettingValue(OPENAI_COMPATIBLE_API_KEY_SETTING);
            if (!isBlank(key)) {
                return new AIProviderConfig("openai_compatible", "通用 OpenAI 兼容", key,
                        normalizeOpenAiBaseUrl(getSettingValue(OPENAI_COMPATIBLE_BASE_URL_SETTING), "https://api.openai.com/v1"),
                        valueOrDefault(getSettingValue(OPENAI_COMPATIBLE_MODEL_SETTING), "gpt-4o-mini"));
            }
        }
        String aliyunKey = getSettingValue(AI_API_KEY_SETTING);
        if (!isBlank(aliyunKey)) {
            return new AIProviderConfig("aliyun", "阿里百炼", aliyunKey,
                    normalizeOpenAiBaseUrl(getSettingValue(AI_BASE_URL_SETTING), DEFAULT_BASE_URL),
                    valueOrDefault(getSettingValue(AI_MODEL_SETTING), DEFAULT_MODEL));
        }
        String openaiKey = getSettingValue(OPENAI_COMPATIBLE_API_KEY_SETTING);
        if (!isBlank(openaiKey)) {
            return new AIProviderConfig("openai_compatible", "通用 OpenAI 兼容", openaiKey,
                    normalizeOpenAiBaseUrl(getSettingValue(OPENAI_COMPATIBLE_BASE_URL_SETTING), "https://api.openai.com/v1"),
                    valueOrDefault(getSettingValue(OPENAI_COMPATIBLE_MODEL_SETTING), "gpt-4o-mini"));
        }
        return new AIProviderConfig("aliyun", "阿里百炼", null, DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    public AIProviderConfig getProviderConfigById(Long id) {
        XianyuAiProvider provider = aiProviderService.getById(id);
        if (provider == null) {
            return null;
        }
        return new AIProviderConfig(
                String.valueOf(provider.getId()),
                provider.getName(),
                provider.getApiKey(),
                normalizeOpenAiBaseUrl(provider.getBaseUrl(), DEFAULT_BASE_URL),
                valueOrDefault(provider.getModel(), DEFAULT_MODEL)
        );
    }

    /**
     * 构建ChatClient实例
     */
    private ChatClient buildChatClient(String apiKey, String baseUrl, String model) {
        OpenAiEndpoint endpoint = resolveOpenAiEndpoint(baseUrl, DEFAULT_BASE_URL);
        String effectiveModel = (model != null && !model.trim().isEmpty()) ? model.trim() : DEFAULT_MODEL;

        // 创建OpenAiApi实例
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(new SimpleApiKey(apiKey.trim()))
                .baseUrl(endpoint.getBaseUrl())
                .completionsPath(endpoint.getCompletionsPath())
                .build();

        // 创建ChatModel
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(effectiveModel)
                .temperature(0.7)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        // 创建ChatClient
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个闲鱼智能客服助手")
                .build();
    }

    private String getSettingValue(String key) {
        try {
            return sysSettingService.getSettingValue(key);
        } catch (Exception e) {
            log.warn("[DynamicAI] 读取配置失败: key={}", key, e);
            return null;
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public static String normalizeOpenAiBaseUrl(String baseUrl, String defaultBaseUrl) {
        String normalized = valueOrDefault(baseUrl, defaultBaseUrl);
        normalized = normalized.replaceAll("/+$", "");
        if (normalized.matches("(?i).*/v\\d+[a-z0-9-]*$")) {
            return normalized;
        }
        return normalized + "/v1";
    }

    public static String normalizeSpringAiBaseUrl(String baseUrl, String defaultBaseUrl) {
        return resolveOpenAiEndpoint(baseUrl, defaultBaseUrl).getBaseUrl();
    }

    public static OpenAiEndpoint resolveOpenAiEndpoint(String baseUrl, String defaultBaseUrl) {
        String normalizedBaseUrl = normalizeOpenAiBaseUrl(baseUrl, defaultBaseUrl);
        return new OpenAiEndpoint(normalizedBaseUrl, "/chat/completions");
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class AIProviderConfig {
        private final String provider;
        private final String providerName;
        private final String apiKey;
        private final String baseUrl;
        private final String model;

        public AIProviderConfig(String provider, String providerName, String apiKey, String baseUrl, String model) {
            this.provider = provider;
            this.providerName = providerName;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
        }

        public String getProvider() { return provider; }
        public String getProviderName() { return providerName; }
        public String getApiKey() { return apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public String getModel() { return model; }
    }

    /**
     * AI状态信息
     */
    public static class AIStatusInfo {
        private boolean enabled;
        private boolean available;
        private boolean apiKeyConfigured;
        private String message;
        private String baseUrl;
        private String model;
        private String provider;
        private String providerName;
        private int configuredCount;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public boolean isApiKeyConfigured() { return apiKeyConfigured; }
        public void setApiKeyConfigured(boolean apiKeyConfigured) { this.apiKeyConfigured = apiKeyConfigured; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public int getConfiguredCount() { return configuredCount; }
        public void setConfiguredCount(int configuredCount) { this.configuredCount = configuredCount; }
    }

    public static class OpenAiEndpoint {
        private final String baseUrl;
        private final String completionsPath;

        public OpenAiEndpoint(String baseUrl, String completionsPath) {
            this.baseUrl = baseUrl;
            this.completionsPath = completionsPath;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getCompletionsPath() {
            return completionsPath;
        }
    }
}
