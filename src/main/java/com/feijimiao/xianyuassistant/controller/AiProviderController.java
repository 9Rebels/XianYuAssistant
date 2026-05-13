package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.config.rag.DynamicAIChatClientManager;
import com.feijimiao.xianyuassistant.entity.XianyuAiProvider;
import com.feijimiao.xianyuassistant.service.AiProviderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/ai-provider")
public class AiProviderController {

    @Autowired
    private AiProviderService aiProviderService;

    @Autowired
    private DynamicAIChatClientManager dynamicAIChatClientManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @PostMapping("/list")
    public ResultObject<List<AiProviderRespDTO>> list() {
        List<XianyuAiProvider> providers = aiProviderService.listAll();
        List<AiProviderRespDTO> result = providers.stream().map(this::toDTO).toList();
        return ResultObject.success(result);
    }

    @PostMapping("/save")
    public ResultObject<?> save(@RequestBody AiProviderSaveReqDTO req) {
        XianyuAiProvider provider;
        if (req.getId() != null) {
            provider = aiProviderService.getById(req.getId());
            if (provider == null) {
                return ResultObject.failed("提供商不存在");
            }
        } else {
            provider = new XianyuAiProvider();
        }
        provider.setName(req.getName());
        provider.setApiKey(req.getApiKey());
        provider.setBaseUrl(req.getBaseUrl());
        provider.setModel(req.getModel());
        if (req.getSortOrder() != null) {
            provider.setSortOrder(req.getSortOrder());
        }
        aiProviderService.save(provider);
        return ResultObject.success(null);
    }

    @PostMapping("/delete")
    public ResultObject<?> delete(@RequestBody IdReqDTO req) {
        if (req.getId() == null) {
            return ResultObject.failed("id 不能为空");
        }
        aiProviderService.deleteById(req.getId());
        return ResultObject.success(null);
    }

    @PostMapping("/activate")
    public ResultObject<?> activate(@RequestBody IdReqDTO req) {
        if (req.getId() == null) {
            return ResultObject.failed("id 不能为空");
        }
        aiProviderService.activate(req.getId());
        return ResultObject.success(null);
    }

    @PostMapping("/test")
    public ResultObject<TestRespDTO> test(@RequestBody AiProviderTestReqDTO req) {
        long startedAt = System.currentTimeMillis();
        try {
            String apiKey;
            String baseUrl;
            String model;

            if (req.getId() != null) {
                DynamicAIChatClientManager.AIProviderConfig config = dynamicAIChatClientManager.getProviderConfigById(req.getId());
                if (config == null) {
                    return ResultObject.failed("提供商不存在");
                }
                apiKey = config.getApiKey();
                baseUrl = config.getBaseUrl();
                model = config.getModel();
            } else {
                apiKey = req.getApiKey();
                baseUrl = DynamicAIChatClientManager.normalizeOpenAiBaseUrl(req.getBaseUrl(), "https://api.openai.com/v1");
                model = req.getModel();
            }

            if (isBlank(apiKey)) {
                return ResultObject.failed("API Key 未配置");
            }
            if (isBlank(model)) {
                return ResultObject.failed("模型未配置");
            }

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "max_tokens", 32,
                    "messages", List.of(Map.of("role", "user", "content", "请回复 ok"))
            ));
            String responseText = requestText(baseUrl + "/chat/completions", apiKey, requestBody);
            JsonNode root = objectMapper.readTree(responseText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.trim().isEmpty()) {
                return ResultObject.failed("测试连接失败: AI返回为空");
            }

            TestRespDTO resp = new TestRespDTO();
            resp.setOk(true);
            resp.setDurationMs(System.currentTimeMillis() - startedAt);
            resp.setResponseSummary(content.trim());
            resp.setMessage("连接成功");
            return ResultObject.success(resp);
        } catch (Exception e) {
            return ResultObject.failed("测试连接失败: " + truncate(e.getMessage()));
        }
    }

    @PostMapping("/models")
    public ResultObject<ModelsRespDTO> models(@RequestBody AiProviderTestReqDTO req) {
        try {
            String apiKey;
            String baseUrl;

            if (req.getId() != null) {
                DynamicAIChatClientManager.AIProviderConfig config = dynamicAIChatClientManager.getProviderConfigById(req.getId());
                if (config == null) {
                    return ResultObject.failed("提供商不存在");
                }
                apiKey = config.getApiKey();
                baseUrl = config.getBaseUrl();
            } else {
                apiKey = req.getApiKey();
                baseUrl = DynamicAIChatClientManager.normalizeOpenAiBaseUrl(req.getBaseUrl(), "https://api.openai.com/v1");
            }

            if (isBlank(apiKey)) {
                return ResultObject.failed("API Key 未配置");
            }

            String responseText = requestText(baseUrl + "/models", apiKey, null);
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode dataNode = root.path("data");
            List<String> models = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    String id = item.path("id").asText("");
                    if (!id.isEmpty()) {
                        models.add(id);
                    }
                }
            }

            ModelsRespDTO resp = new ModelsRespDTO();
            resp.setModels(models);
            return ResultObject.success(resp);
        } catch (Exception e) {
            return ResultObject.failed("获取模型失败: " + truncate(e.getMessage()));
        }
    }

    private AiProviderRespDTO toDTO(XianyuAiProvider entity) {
        AiProviderRespDTO dto = new AiProviderRespDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setApiKey(maskApiKey(entity.getApiKey()));
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setModel(entity.getModel());
        dto.setIsActive(entity.getIsActive());
        dto.setEnabled(entity.getEnabled());
        dto.setSortOrder(entity.getSortOrder());
        return dto;
    }

    private String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) return "";
        if (apiKey.length() <= 8) return "****";
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    @Data
    public static class IdReqDTO {
        private Long id;
    }

    @Data
    public static class AiProviderSaveReqDTO {
        private Long id;
        private String name;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer sortOrder;
    }

    @Data
    public static class AiProviderTestReqDTO {
        private Long id;
        private String apiKey;
        private String baseUrl;
        private String model;
    }

    @Data
    public static class AiProviderRespDTO {
        private Long id;
        private String name;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer isActive;
        private Integer enabled;
        private Integer sortOrder;
    }

    @Data
    public static class TestRespDTO {
        private boolean ok;
        private long durationMs;
        private String responseSummary;
        private String message;
    }

    @Data
    public static class ModelsRespDTO {
        private List<String> models;
    }
}