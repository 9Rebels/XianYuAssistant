package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.config.rag.DynamicAIChatClientManager;
import com.feijimiao.xianyuassistant.controller.dto.ChatWithAIReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.DeleteRAGDataReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.PutNewDataToRAGReqDTO;
import com.feijimiao.xianyuassistant.service.AIService;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.bo.RAGDataRespBO;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI对话控制器
 * 始终加载，AI功能未配置时自动降级
 *
 * @author IAMLZY
 * @date 2026/4/12 00:16
 */
@RestController
@RequestMapping("/ai")
public class AIChatController {
    private static final String GLOBAL_AI_REPLY_TEMPLATE_PREFIX = "global_ai_reply_template_";

    @Autowired
    private AIService aiService;

    @Autowired
    private DynamicAIChatClientManager dynamicAIChatClientManager;
    
    @Autowired
    private GoodsInfoService goodsInfoService;

    @Autowired
    private SysSettingService sysSettingService;
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * AI对话（流式返回）
     * 未配置API Key时返回降级提示
     */
    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithAi(@RequestBody ChatWithAIReqDTO chatWithAIReqDTO) {
        return aiService.chatByRAG(chatWithAIReqDTO.getMsg(), chatWithAIReqDTO.getGoodsId());
    }

    /**
     * AI对话测试接口（流式）- 与自动回复流程一致
     * 携带固定资料和商品详情，用于测试提示词与资料效果
     */
    @PostMapping(path = "/chatTest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatTestWithAi(@RequestBody ChatTestReqDTO req) {
        String fixedMaterial = null;
        String goodsDetail = null;
        
        if (req.getAccountId() != null && req.getGoodsId() != null) {
            XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(req.getAccountId(), req.getGoodsId());
            if (config != null) {
                fixedMaterial = buildFixedMaterial(req.getAccountId(), config.getFixedMaterial());
            } else {
                fixedMaterial = buildFixedMaterial(req.getAccountId(), null);
            }
            
            String detailInfo = goodsInfoService.getDetailInfoByGoodsId(req.getGoodsId());
            if (detailInfo != null && !detailInfo.isEmpty()) {
                goodsDetail = detailInfo;
            }
        }
        
        return aiService.chatByRAGWithFixedMaterialStream(req.getMsg(), req.getGoodsId(), fixedMaterial, goodsDetail);
    }

    private String buildFixedMaterial(Long accountId, String goodsFixedMaterial) {
        String globalTemplate = getGlobalAiReplyTemplate(accountId);
        boolean hasGlobal = globalTemplate != null && !globalTemplate.trim().isEmpty();
        boolean hasGoods = goodsFixedMaterial != null && !goodsFixedMaterial.trim().isEmpty();
        if (!hasGlobal && !hasGoods) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (hasGlobal) {
            builder.append("通用回复模板：\n").append(globalTemplate.trim());
        }
        if (hasGoods) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("当前商品补充资料：\n").append(goodsFixedMaterial.trim());
        }
        return builder.toString();
    }

    private String getGlobalAiReplyTemplate(Long accountId) {
        if (accountId == null) {
            return "";
        }
        String value = sysSettingService.getSettingValue(GLOBAL_AI_REPLY_TEMPLATE_PREFIX + accountId);
        return value == null ? "" : value;
    }

    /**
     * AI状态检测接口
     * 返回AI服务是否可用、配置状态等信息
     */
    @PostMapping("/status")
    public ResultObject<AIStatusRespDTO> getAIStatus() {
        DynamicAIChatClientManager.AIStatusInfo statusInfo = dynamicAIChatClientManager.getStatusInfo();

        AIStatusRespDTO respDTO = new AIStatusRespDTO();
        respDTO.setEnabled(statusInfo.isEnabled());
        respDTO.setAvailable(statusInfo.isAvailable());
        respDTO.setApiKeyConfigured(statusInfo.isApiKeyConfigured());
        respDTO.setMessage(statusInfo.getMessage());
        respDTO.setBaseUrl(statusInfo.getBaseUrl());
        respDTO.setModel(statusInfo.getModel());
        respDTO.setProvider(statusInfo.getProvider());
        respDTO.setProviderName(statusInfo.getProviderName());
        respDTO.setConfiguredCount(statusInfo.getConfiguredCount());

        return ResultObject.success(respDTO);
    }

    @PostMapping("/models")
    public ResultObject<AIModelsRespDTO> getModels(@RequestBody AIProviderReqDTO req) {
        try {
            DynamicAIChatClientManager.AIProviderConfig config = resolveProviderConfig(req);
            validateConfigForModels(config);

            String responseText = requestText(config.getBaseUrl() + "/models", config.getApiKey(), null);
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode dataNode = root.path("data");
            List<String> models = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    String model = textValue(item, "id");
                    if (!model.isEmpty()) {
                        models.add(model);
                    }
                }
            }

            AIModelsRespDTO resp = new AIModelsRespDTO();
            resp.setProvider(config.getProvider());
            resp.setProviderName(config.getProviderName());
            resp.setBaseUrl(config.getBaseUrl());
            resp.setModels(models);
            return ResultObject.success(resp);
        } catch (Exception e) {
            return ResultObject.failed("获取模型失败: " + readableMessage(e));
        }
    }

    @PostMapping("/test")
    public ResultObject<AITestRespDTO> testConnection(@RequestBody AIProviderReqDTO req) {
        long startedAt = System.currentTimeMillis();
        try {
            DynamicAIChatClientManager.AIProviderConfig config = resolveProviderConfig(req);
            validateConfigForChat(config);

            String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", config.getModel(),
                    "temperature", 0.2,
                    "max_tokens", 32,
                    "messages", List.of(java.util.Map.of("role", "user", "content", "请回复 ok"))
            ));
            String responseText = requestText(config.getBaseUrl() + "/chat/completions", config.getApiKey(), requestBody);
            JsonNode root = objectMapper.readTree(responseText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content == null || content.trim().isEmpty()) {
                return ResultObject.failed("测试连接失败: AI返回为空");
            }

            AITestRespDTO resp = new AITestRespDTO();
            resp.setOk(true);
            resp.setProvider(config.getProvider());
            resp.setProviderName(config.getProviderName());
            resp.setBaseUrl(config.getBaseUrl());
            resp.setModel(config.getModel());
            resp.setDurationMs(System.currentTimeMillis() - startedAt);
            resp.setResponseSummary(content.trim());
            resp.setMessage("连接成功，当前模型可正常返回内容");
            return ResultObject.success(resp);
        } catch (Exception e) {
            return ResultObject.failed("测试连接失败: " + readableMessage(e));
        }
    }

    @PostMapping("/putNewData")
    public ResultObject<?> putNewData(@RequestBody PutNewDataToRAGReqDTO putNewDataToRAGReqDTO) {
        try {
            aiService.putDataToRAG(putNewDataToRAGReqDTO.getContent(), putNewDataToRAGReqDTO.getGoodsId());
            return ResultObject.success(null);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("向量库未初始化")) {
                return ResultObject.failed(1001, "请完成AI配置再上传资料");
            }
            throw e;
        }
    }

    @PostMapping("/queryRAGData")
    public ResultObject<List<RAGDataRespBO>> queryRAGData(@RequestBody PutNewDataToRAGReqDTO req) {
        List<RAGDataRespBO> data = aiService.queryRAGDataBygoodsId(req.getGoodsId());
        return ResultObject.success(data);
    }

    @PostMapping("/deleteRAGData")
    public ResultObject<?> deleteRAGData(@RequestBody DeleteRAGDataReqDTO req) {
        aiService.deleteRAGDataByDocumentId(req.getDocumentId());
        return ResultObject.success(null);
    }

    @PostMapping("/saveFixedMaterial")
    public ResultObject<?> saveFixedMaterial(@RequestBody FixedMaterialReqDTO req) {
        goodsConfigMapper.updateFixedMaterial(req.getAccountId(), req.getGoodsId(), req.getFixedMaterial());
        return ResultObject.success(null);
    }

    @PostMapping("/getFixedMaterial")
    public ResultObject<FixedMaterialRespDTO> getFixedMaterial(@RequestBody FixedMaterialReqDTO req) {
        XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(req.getAccountId(), req.getGoodsId());
        FixedMaterialRespDTO resp = new FixedMaterialRespDTO();
        if (config != null) {
            resp.setFixedMaterial(config.getFixedMaterial());
        }
        return ResultObject.success(resp);
    }

    @PostMapping("/syncDetailToFixedMaterial")
    public ResultObject<?> syncDetailToFixedMaterial(@RequestBody FixedMaterialReqDTO req) {
        String detailInfo = goodsInfoService.getDetailInfoByGoodsId(req.getGoodsId());
        if (detailInfo != null && !detailInfo.isEmpty()) {
            goodsConfigMapper.updateFixedMaterial(req.getAccountId(), req.getGoodsId(), detailInfo);
            return ResultObject.success(null);
        } else {
            return ResultObject.failed("商品详情为空，无法同步");
        }
    }

    public static class FixedMaterialReqDTO {
        private Long accountId;
        private String goodsId;
        private String fixedMaterial;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getGoodsId() { return goodsId; }
        public void setGoodsId(String goodsId) { this.goodsId = goodsId; }
        public String getFixedMaterial() { return fixedMaterial; }
        public void setFixedMaterial(String fixedMaterial) { this.fixedMaterial = fixedMaterial; }
    }

    public static class FixedMaterialRespDTO {
        private String fixedMaterial;

        public String getFixedMaterial() { return fixedMaterial; }
        public void setFixedMaterial(String fixedMaterial) { this.fixedMaterial = fixedMaterial; }
    }

    public static class ChatTestReqDTO {
        private Long accountId;
        private String goodsId;
        private String msg;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public String getGoodsId() { return goodsId; }
        public void setGoodsId(String goodsId) { this.goodsId = goodsId; }
        public String getMsg() { return msg; }
        public void setMsg(String msg) { this.msg = msg; }
    }

    /**
     * AI状态响应DTO
     */
    public static class AIStatusRespDTO {
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

    public static class AIProviderReqDTO {
        private String provider;
        private String apiKey;
        private String baseUrl;
        private String model;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class AIModelsRespDTO {
        private String provider;
        private String providerName;
        private String baseUrl;
        private List<String> models;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public List<String> getModels() { return models; }
        public void setModels(List<String> models) { this.models = models; }
    }

    public static class AITestRespDTO {
        private boolean ok;
        private String provider;
        private String providerName;
        private String baseUrl;
        private String model;
        private long durationMs;
        private String responseSummary;
        private String message;

        public boolean isOk() { return ok; }
        public void setOk(boolean ok) { this.ok = ok; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getResponseSummary() { return responseSummary; }
        public void setResponseSummary(String responseSummary) { this.responseSummary = responseSummary; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    private DynamicAIChatClientManager.AIProviderConfig resolveProviderConfig(AIProviderReqDTO req) {
        // 如果请求中提供了完整的临时参数，直接使用
        String apiKey = req != null ? req.getApiKey() : null;
        String baseUrl = req != null ? req.getBaseUrl() : null;
        String model = req != null ? req.getModel() : null;

        DynamicAIChatClientManager.AIProviderConfig activeConfig = dynamicAIChatClientManager.resolveActiveProviderConfig();

        String effectiveApiKey = trimOrDefault(apiKey, activeConfig.getApiKey());
        String effectiveBaseUrl = trimOrDefault(baseUrl, activeConfig.getBaseUrl());
        String effectiveModel = trimOrDefault(model, activeConfig.getModel());
        String normalizedBaseUrl = DynamicAIChatClientManager.normalizeOpenAiBaseUrl(effectiveBaseUrl, activeConfig.getBaseUrl());

        return new DynamicAIChatClientManager.AIProviderConfig(
                activeConfig.getProvider(),
                activeConfig.getProviderName(),
                effectiveApiKey,
                normalizedBaseUrl,
                effectiveModel
        );
    }

    private String requestText(String url, String apiKey, String jsonBody) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey);
        if (jsonBody == null) {
            builder.get();
        } else {
            builder.post(okhttp3.RequestBody.create(jsonBody, okhttp3.MediaType.parse("application/json")));
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(response.code() + " " + truncate(body));
            }
            return body;
        }
    }

    private static void validateConfigForModels(DynamicAIChatClientManager.AIProviderConfig config) {
        if (isBlank(config.getApiKey())) {
            throw new IllegalArgumentException(config.getProviderName() + " API Key未配置");
        }
        if (isBlank(config.getBaseUrl())) {
            throw new IllegalArgumentException(config.getProviderName() + " Base URL未配置");
        }
    }

    private static void validateConfigForChat(DynamicAIChatClientManager.AIProviderConfig config) {
        validateConfigForModels(config);
        if (isBlank(config.getModel())) {
            throw new IllegalArgumentException(config.getProviderName() + " 模型未配置");
        }
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private static String trimOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String readableMessage(Exception e) {
        return truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
