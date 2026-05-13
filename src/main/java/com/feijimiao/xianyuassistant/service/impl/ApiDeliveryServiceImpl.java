package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.service.ApiDeliveryService;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryResult;
import com.feijimiao.xianyuassistant.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认API发货实现：固定协议 v1，POST JSON + X-Delivery-Key。
 */
@Slf4j
@Service
public class ApiDeliveryServiceImpl implements ApiDeliveryService {

    private static final String STANDARD_AUTH_HEADER = "X-Delivery-Key";
    private static final String DEFAULT_CONTENT_PATH = "data.content";
    private static final String DEFAULT_ALLOCATION_ID_PATH = "data.allocationId";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ApiDeliveryResult allocate(XianyuGoodsAutoDeliveryConfig config, ApiDeliveryContext context) {
        ApiDeliveryResult result = call(config.getApiAllocateUrl(), config, context, "allocate");
        if (!result.isSuccess()) {
            return result;
        }
        if (isBlank(result.getContent())) {
            return ApiDeliveryResult.of(false, null, result.getAllocationId(), "API响应缺少发货内容");
        }
        return result;
    }

    @Override
    public ApiDeliveryResult confirm(XianyuGoodsAutoDeliveryConfig config, ApiDeliveryContext context) {
        if (isBlank(config.getApiConfirmUrl())) {
            return ApiDeliveryResult.of(true, null, context.getAllocationId(), "未配置确认接口，已跳过");
        }
        return call(config.getApiConfirmUrl(), config, context, "confirm");
    }

    @Override
    public ApiDeliveryResult returnAllocation(XianyuGoodsAutoDeliveryConfig config, ApiDeliveryContext context) {
        if (isBlank(config.getApiReturnUrl())) {
            return ApiDeliveryResult.of(true, null, context.getAllocationId(), "未配置回库接口，已跳过");
        }
        return call(config.getApiReturnUrl(), config, context, "return");
    }

    private ApiDeliveryResult call(String url, XianyuGoodsAutoDeliveryConfig config,
                                   ApiDeliveryContext context, String action) {
        if (isBlank(url)) {
            return ApiDeliveryResult.of(false, null, context.getAllocationId(), "API接口URL未配置");
        }
        try {
            String body = objectMapper.writeValueAsString(buildBody(context, action));
            String response = HttpClientUtils.postJson(url, buildHeaders(config), body);
            if (isBlank(response)) {
                return ApiDeliveryResult.of(false, null, context.getAllocationId(), "API响应为空");
            }
            return parseResponse(response, config, context.getAllocationId(), action);
        } catch (Exception e) {
            log.error("API发货调用异常: action={}, url={}", action, url, e);
            return ApiDeliveryResult.of(false, null, context.getAllocationId(), e.getMessage());
        }
    }

    private Map<String, Object> buildBody(ApiDeliveryContext context, String action) {
        Map<String, Object> body = new LinkedHashMap<>();

        // 通用API发货协议v1标准字段
        body.put("provider", "xianyu");
        body.put("orderId", context.getOrderId());
        body.put("goodsId", context.getXyGoodsId());
        body.put("buyerUserId", context.getBuyerUserId());
        body.put("buyerUserName", context.getBuyerUserName());
        body.put("buyQuantity", context.getBuyQuantity() != null ? context.getBuyQuantity() : 1);
        body.put("deliveryIndex", context.getDeliveryIndex() != null ? context.getDeliveryIndex() : 1);
        body.put("deliveryTotal", context.getDeliveryTotal() != null ? context.getDeliveryTotal() : 1);

        // 可选字段
        if (!isBlank(context.getTriggerSource())) {
            body.put("triggerSource", context.getTriggerSource());
        }
        if (!isBlank(context.getTriggerContent())) {
            body.put("triggerContent", context.getTriggerContent());
        }

        // confirm和return操作需要allocationId
        if ("confirm".equals(action) || "return".equals(action)) {
            body.put("allocationId", context.getAllocationId());
        }

        // return操作需要reason
        if ("return".equals(action) && !isBlank(context.getReason())) {
            body.put("reason", context.getReason());
        }

        // 元数据：保留内部字段供调试和追踪
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("recordId", context.getRecordId());
        metadata.put("accountId", context.getAccountId());
        metadata.put("xianyuGoodsId", context.getXianyuGoodsId());
        metadata.put("ruleId", context.getRuleId());
        metadata.put("ruleName", context.getRuleName());
        body.put("metadata", metadata);

        mergeCustomRequestExtras(body, context.getApiRequestExtras());
        return body;
    }

    private Map<String, String> buildHeaders(XianyuGoodsAutoDeliveryConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (!isBlank(config.getApiHeaderValue())) {
            headers.put(STANDARD_AUTH_HEADER, config.getApiHeaderValue().trim());
        }
        return headers;
    }

    private ApiDeliveryResult parseResponse(String response, XianyuGoodsAutoDeliveryConfig config,
                                            String fallbackAllocationId, String action) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        if (!isSuccessResponse(root)) {
            return ApiDeliveryResult.of(false, null, fallbackAllocationId, responseMessage(root));
        }
        String content = textAt(root, DEFAULT_CONTENT_PATH);
        String allocationId = textAt(root, DEFAULT_ALLOCATION_ID_PATH);
        if (isBlank(allocationId)) {
            allocationId = fallbackAllocationId;
        }
        return ApiDeliveryResult.of(true, content, allocationId, action + " success");
    }

    private void mergeCustomRequestExtras(Map<String, Object> body, String extrasJson) {
        if (isBlank(extrasJson)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(extrasJson);
            if (node == null || !node.isObject()) {
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String key = entry.getKey();
                if (isBlank(key) || body.containsKey(key)) {
                    continue;
                }
                body.put(key, objectMapper.convertValue(entry.getValue(), Object.class));
            }
        } catch (Exception e) {
            log.warn("API发货附加请求JSON解析失败，已忽略: {}", e.getMessage());
        }
    }

    private boolean isSuccessResponse(JsonNode root) {
        JsonNode codeNode = root.get("code");
        if (codeNode == null || codeNode.isNull()) {
            return true;
        }
        if (codeNode.isNumber()) {
            int code = codeNode.asInt();
            return code == 0 || code == 200;
        }
        String code = codeNode.asText();
        return "0".equals(code) || "200".equals(code) || "success".equalsIgnoreCase(code);
    }

    private String responseMessage(JsonNode root) {
        String msg = textAt(root, "message");
        if (isBlank(msg)) {
            msg = textAt(root, "msg");
        }
        return isBlank(msg) ? root.toString() : msg;
    }

    static String textAt(JsonNode root, String path) {
        if (root == null || isBlank(path)) {
            return null;
        }
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (isBlank(part)) {
                return null;
            }
            current = current.get(part);
            if (current == null || current.isNull()) {
                return null;
            }
        }
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    static String renderApiDeliveryMessage(String template, String apiContent) {
        if (isBlank(template)) {
            return apiContent;
        }
        String content = apiContent == null ? "" : apiContent;
        return template.replace("{apiContent}", content);
    }
}
