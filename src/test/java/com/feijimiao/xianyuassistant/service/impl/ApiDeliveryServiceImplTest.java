package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiDeliveryServiceImplTest {

    @Test
    void buildHeadersUsesFixedDeliveryKeyHeader() throws Exception {
        ApiDeliveryServiceImpl service = new ApiDeliveryServiceImpl();
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setApiHeaderValue(" secret-value ");

        Method method = ApiDeliveryServiceImpl.class
                .getDeclaredMethod("buildHeaders", XianyuGoodsAutoDeliveryConfig.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) method.invoke(service, config);

        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals("secret-value", headers.get("X-Delivery-Key"));
        assertFalse(headers.containsKey("X-Other-Key"));
    }

    @Test
    void textAtReadsFixedNestedPaths() {
        String json = """
                {
                  "code": 0,
                  "data": {
                    "content": "账号：13800000000\\n密码：123456",
                    "allocationId": "dml_001"
                  }
                }
                """;

        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals("账号：13800000000\n密码：123456", ApiDeliveryServiceImpl.textAt(root, "data.content"));
        assertEquals("dml_001", ApiDeliveryServiceImpl.textAt(root, "data.allocationId"));
        assertNull(ApiDeliveryServiceImpl.textAt(root, "data.deliveryId"));
        assertNull(ApiDeliveryServiceImpl.textAt(root, ""));
        assertNull(ApiDeliveryServiceImpl.textAt(null, "data.content"));
    }

    @Test
    void buildHeadersSkipsDeliveryKeyWhenValueBlank() throws Exception {
        ApiDeliveryServiceImpl service = new ApiDeliveryServiceImpl();
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setApiHeaderValue("   ");

        Method method = ApiDeliveryServiceImpl.class
                .getDeclaredMethod("buildHeaders", XianyuGoodsAutoDeliveryConfig.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) method.invoke(service, config);

        assertEquals("application/json", headers.get("Content-Type"));
        assertTrue(headers.size() == 1);
    }

    @Test
    void buildBodyMergesCustomRequestExtrasWithoutOverridingStandardFields() throws Exception {
        ApiDeliveryServiceImpl service = new ApiDeliveryServiceImpl();

        Method method = ApiDeliveryServiceImpl.class
                .getDeclaredMethod("buildBody", com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext.class, String.class);
        method.setAccessible(true);

        com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext context =
                com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext.builder()
                        .orderId("ORDER-1")
                        .xyGoodsId("GOODS-1")
                        .buyerUserName("买家A")
                        .apiRequestExtras("{\"ruleCode\":\"croissant_free9\",\"provider\":\"bad\",\"ext\":{\"scene\":\"api\"}}")
                        .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) method.invoke(service, context, "allocate");

        assertEquals("xianyu", body.get("provider"));
        assertEquals("ORDER-1", body.get("orderId"));
        assertEquals("croissant_free9", body.get("ruleCode"));
        assertTrue(body.containsKey("ext"));
    }

    @Test
    void renderApiDeliveryMessageSupportsPlaceholder() {
        assertEquals("账号如下：A001", ApiDeliveryServiceImpl.renderApiDeliveryMessage("账号如下：{apiContent}", "A001"));
        assertEquals("A001", ApiDeliveryServiceImpl.renderApiDeliveryMessage(null, "A001"));
    }
}
