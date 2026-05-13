package com.feijimiao.xianyuassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 订单批量刷新服务
 *
 * 功能：
 * - 使用浏览器池查询订单详情
 * - 同账号内顺序处理，避免并发页面互相关闭浏览器
 * - 拦截API获取完整订单数据（包含收货人信息）
 * - 自动更新数据库
 */
@Slf4j
@Service
public class OrderBatchRefreshService {

    private static final String ORDER_DETAIL_API = "mtop.idle.web.trade.order.detail";
    private static final int ORDER_REFRESH_TIMEOUT_MS = 30000;
    private static final int ORDER_API_RESPONSE_TIMEOUT_MS = 45000;
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private BrowserPool browserPool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 订单详情结果
     */
    @Data
    public static class OrderDetailResult {
        private boolean success;
        private String orderId;
        private String error;

        // 订单基本信息
        private Integer orderStatus;
        private String statusText;
        private String xyGoodsId;
        private String itemTitle;
        private String price;
        private Long orderAmount;
        private String buyerUserId;
        private String buyerUserName;
        private Long orderCreateTime;
        private Long orderPayTime;
        private Long orderDeliveryTime;

        // 收货人信息
        private String receiverName;
        private String receiverPhone;
        private String receiverAddress;
        private String receiverCity;

        // 其他信息
        private boolean canRate;
        private JsonNode rawData;
    }

    /**
     * 批量刷新订单
     *
     * @param accountId 账号ID
     * @param cookieText Cookie字符串
     * @param orderIds 订单ID列表
     * @param headless 是否无头模式
     * @return 订单详情结果列表
     */
    public List<OrderDetailResult> batchRefreshOrders(Long accountId, String cookieText,
                                                      List<String> orderIds, boolean headless) {
        log.info("开始批量刷新订单: accountId={}, 订单数量={}", accountId, orderIds.size());

        List<OrderDetailResult> results = new ArrayList<>();
        for (String orderId : orderIds) {
            try {
                results.add(queryOrderDetail(accountId, cookieText, orderId, headless));
            } catch (Exception e) {
                log.error("获取订单详情失败: orderId={}", orderId, e);
                OrderDetailResult errorResult = new OrderDetailResult();
                errorResult.setOrderId(orderId);
                errorResult.setSuccess(false);
                errorResult.setError("查询超时或失败: " + e.getMessage());
                results.add(errorResult);
            }
        }

        log.info("批量刷新订单完成: accountId={}, 成功={}, 失败={}",
            accountId,
            results.stream().filter(OrderDetailResult::isSuccess).count(),
            results.stream().filter(r -> !r.isSuccess()).count());

        return results;
    }

    /**
     * 查询单个订单详情
     */
    private OrderDetailResult queryOrderDetail(Long accountId, String cookieText,
                                               String orderId, boolean headless) {
        OrderDetailResult result = new OrderDetailResult();
        result.setOrderId(orderId);

        BrowserPool.BrowserInstance browserInstance = null;
        Page page = null;

        try {
            // 从浏览器池获取浏览器实例（创建新页面避免并发冲突）
            browserInstance = browserPool.getBrowser(accountId, cookieText, headless, true);
            if (browserInstance == null) {
                result.setSuccess(false);
                result.setError("无法获取浏览器实例");
                return result;
            }

            page = browserInstance.getPage();

            // 访问订单详情页面
            String pageUrl = "https://www.goofish.com/order-detail?orderId=" + orderId + "&role=seller";
            log.debug("访问订单详情页面: {}", pageUrl);

            Response apiResponse = waitForOrderDetailResponse(page, pageUrl);
            parseApiResponse(new String(apiResponse.body(), StandardCharsets.UTF_8), result);

        } catch (Exception e) {
            log.error("查询订单详情失败: orderId={}", orderId, e);
            result.setSuccess(false);
            result.setError("查询失败: " + e.getMessage());
        } finally {
            // 关闭页面（但不关闭浏览器，由浏览器池管理）
            if (page != null && !page.isClosed()) {
                try {
                    page.close();
                } catch (Exception e) {
                    log.debug("关闭页面失败: {}", e.getMessage());
                }
            }
        }

        return result;
    }

    private Response waitForOrderDetailResponse(Page page, String pageUrl) {
        Predicate<Response> isOrderDetailResponse = response ->
            response.url().contains(ORDER_DETAIL_API);

        return page.waitForResponse(
            isOrderDetailResponse,
            new Page.WaitForResponseOptions().setTimeout(ORDER_API_RESPONSE_TIMEOUT_MS),
            () -> {
                page.navigate(pageUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(ORDER_REFRESH_TIMEOUT_MS));

                page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
                page.evaluate("window.scrollTo(0, 0)");
            }
        );
    }

    private void parseApiResponse(String responseBody, OrderDetailResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        JsonNode retNode = jsonNode.get("ret");
        if (retNode == null || !retNode.isArray() || retNode.isEmpty()) {
            result.setSuccess(false);
            result.setError("API响应格式错误");
            return;
        }

        String retValue = retNode.get(0).asText();
        if (!retValue.startsWith("SUCCESS")) {
            result.setSuccess(false);
            result.setError("API返回错误: " + retValue);
            return;
        }

        JsonNode dataNode = jsonNode.get("data");
        if (dataNode == null) {
            result.setSuccess(false);
            result.setError("API响应中没有data字段");
            return;
        }

        parseOrderData(dataNode, result);
        result.setSuccess(true);
        result.setRawData(dataNode);
    }

    /**
     * 解析订单数据
     */
    private void parseOrderData(JsonNode dataNode, OrderDetailResult result) {
        try {
            // 提取订单状态
            JsonNode statusNode = dataNode.get("status");
            if (statusNode != null) {
                result.setOrderStatus(statusNode.asInt());
            }

            JsonNode utArgsNode = dataNode.get("utArgs");
            if (utArgsNode != null) {
                JsonNode statusNameNode = utArgsNode.get("orderStatusName");
                if (statusNameNode != null) {
                    result.setStatusText(statusNameNode.asText());
                }
            }

            setIfPresent(dataNode, "peerUserId", result::setBuyerUserId);
            setIfPresent(dataNode, "itemId", result::setXyGoodsId);

            // 提取商品信息和收货人信息
            JsonNode componentsNode = dataNode.get("components");
            if (componentsNode != null && componentsNode.isArray()) {
                for (JsonNode component : componentsNode) {
                    String render = component.has("render") ? component.get("render").asText() : "";

                    if ("orderInfoVO".equals(render)) {
                        JsonNode componentData = component.get("data");
                        if (componentData != null) {
                            // 提取商品信息
                            JsonNode itemInfo = componentData.get("itemInfo");
                            if (itemInfo != null) {
                                setGoodsIdIfMissing(itemInfo, "itemId", result);
                                setGoodsIdIfMissing(itemInfo, "id", result);
                                if (itemInfo.has("title")) {
                                    result.setItemTitle(itemInfo.get("title").asText());
                                }
                            }

                            // 提取价格
                            JsonNode priceInfo = componentData.get("priceInfo");
                            if (priceInfo != null) {
                                JsonNode amount = priceInfo.get("amount");
                                if (amount != null && amount.has("value")) {
                                    String price = amount.get("value").asText();
                                    result.setPrice(price);
                                    result.setOrderAmount(toCent(price));
                                }
                            }

                            // 提取收货人信息
                            JsonNode addressInfo = componentData.get("addressInfo");
                            if (addressInfo != null) {
                                if (addressInfo.has("receiverName")) {
                                    result.setReceiverName(addressInfo.get("receiverName").asText());
                                }
                                if (addressInfo.has("receiverMobile")) {
                                    result.setReceiverPhone(addressInfo.get("receiverMobile").asText());
                                }

                                // 构建完整地址
                                StringBuilder addressBuilder = new StringBuilder();
                                if (addressInfo.has("province")) {
                                    addressBuilder.append(addressInfo.get("province").asText());
                                }
                                if (addressInfo.has("city")) {
                                    String city = addressInfo.get("city").asText();
                                    addressBuilder.append(" ").append(city);
                                    result.setReceiverCity(city);
                                }
                                if (addressInfo.has("district")) {
                                    addressBuilder.append(" ").append(addressInfo.get("district").asText());
                                }
                                if (addressInfo.has("detailAddress")) {
                                    addressBuilder.append(" ").append(addressInfo.get("detailAddress").asText());
                                }

                                if (addressInfo.has("fullAddress")) {
                                    result.setReceiverAddress(addressInfo.get("fullAddress").asText());
                                } else if (addressBuilder.length() > 0) {
                                    result.setReceiverAddress(addressBuilder.toString().trim());
                                }
                            }

                            parseOrderInfoList(componentData.get("orderInfoList"), result);
                        }
                    } else if ("cainiaoLogisticsInfoVO".equals(render)) {
                        parseCainiaoAddress(component.get("data"), result);
                    }
                }
            }

            parseAnyAddress(dataNode, result);

            // 检查是否可评价
            JsonNode bottomBarNode = dataNode.get("bottomBarVO");
            if (bottomBarNode != null) {
                JsonNode buttonListNode = bottomBarNode.get("buttonList");
                if (buttonListNode != null && buttonListNode.isArray()) {
                    for (JsonNode button : buttonListNode) {
                        if (button.has("tradeAction") && "RATE".equals(button.get("tradeAction").asText())) {
                            result.setCanRate(true);
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("解析订单数据失败", e);
        }
    }

    private void parseCainiaoAddress(JsonNode componentData, OrderDetailResult result) {
        if (componentData == null) {
            return;
        }

        JsonNode addressInfo = componentData.get("addressInfoVO");
        if (addressInfo == null) {
            return;
        }

        setIfPresent(addressInfo, "name", result::setReceiverName);
        setIfPresent(addressInfo, "phoneNumber", result::setReceiverPhone);
        setIfPresent(addressInfo, "address", result::setReceiverAddress);
        if (result.getReceiverCity() == null) {
            result.setReceiverCity(extractCityFromAddress(result.getReceiverAddress()));
        }
    }

    private void parseOrderInfoList(JsonNode orderInfoList, OrderDetailResult result) {
        if (orderInfoList == null || !orderInfoList.isArray()) {
            return;
        }

        for (JsonNode item : orderInfoList) {
            String title = text(item.get("title"));
            String value = text(item.get("value"));
            if (title.isBlank() || value.isBlank()) {
                continue;
            }

            if (title.contains("买家昵称")) {
                result.setBuyerUserName(value);
            } else if (title.contains("下单时间")) {
                result.setOrderCreateTime(parseOrderTime(value));
            } else if (title.contains("付款时间")) {
                result.setOrderPayTime(parseOrderTime(value));
            } else if (title.contains("发货时间")) {
                result.setOrderDeliveryTime(parseOrderTime(value));
            } else if (title.contains("收货地址") && result.getReceiverAddress() == null) {
                result.setReceiverAddress(value);
                if (result.getReceiverCity() == null) {
                    result.setReceiverCity(extractCityFromAddress(value));
                }
            }
        }
    }

    private void parseAnyAddress(JsonNode root, OrderDetailResult result) {
        if (root == null || (result.getReceiverName() != null && result.getReceiverPhone() != null && result.getReceiverAddress() != null)) {
            return;
        }
        List<JsonNode> queue = new ArrayList<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < 400) {
            visited++;
            JsonNode node = queue.remove(0);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isObject()) {
                parseAddressCandidate(node, result);
                node.elements().forEachRemaining(queue::add);
            } else if (node.isArray()) {
                node.forEach(queue::add);
            }
        }
        if (result.getReceiverCity() == null) {
            result.setReceiverCity(extractCityFromAddress(result.getReceiverAddress()));
        }
    }

    private void parseAddressCandidate(JsonNode node, OrderDetailResult result) {
        if (!looksLikeAddressNode(node)) {
            return;
        }
        setIfPresent(node, "receiverName", result::setReceiverName);
        setIfPresent(node, "receiverMobile", result::setReceiverPhone);
        setIfPresent(node, "receiverPhone", result::setReceiverPhone);
        setIfPresent(node, "name", result::setReceiverName);
        setIfPresent(node, "phoneNumber", result::setReceiverPhone);
        setIfPresent(node, "mobile", result::setReceiverPhone);
        setIfPresent(node, "tel", result::setReceiverPhone);
        setIfPresent(node, "fullAddress", result::setReceiverAddress);
        setIfPresent(node, "detailAddress", result::setReceiverAddress);
        setIfPresent(node, "address", result::setReceiverAddress);
        setIfPresent(node, "receiverAddress", result::setReceiverAddress);
        setIfPresent(node, "city", result::setReceiverCity);

        if (result.getReceiverAddress() == null) {
            String address = joinAddressParts(node);
            if (!address.isBlank()) {
                result.setReceiverAddress(address);
            }
        }
    }

    private boolean looksLikeAddressNode(JsonNode node) {
        return node.has("addressInfo")
                || node.has("addressInfoVO")
                || node.has("receiverName")
                || node.has("receiverMobile")
                || node.has("receiverAddress")
                || node.has("fullAddress")
                || (node.has("province") && (node.has("city") || node.has("detailAddress")))
                || (node.has("name") && node.has("phoneNumber") && node.has("address"));
    }

    private String joinAddressParts(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        appendAddressPart(builder, text(node.get("province")));
        appendAddressPart(builder, text(node.get("city")));
        appendAddressPart(builder, text(node.get("district")));
        appendAddressPart(builder, text(node.get("area")));
        appendAddressPart(builder, text(node.get("street")));
        appendAddressPart(builder, text(node.get("detailAddress")));
        return builder.toString().trim();
    }

    private void appendAddressPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value.trim());
    }

    private Long parseOrderTime(String value) {
        try {
            return LocalDateTime.parse(value, ORDER_TIME_FORMATTER)
                .atZone(DEFAULT_ZONE)
                .toInstant()
                .toEpochMilli();
        } catch (Exception e) {
            log.debug("解析订单时间失败: {}", value);
            return null;
        }
    }

    private Long toCent(String price) {
        try {
            return new BigDecimal(price.trim()).movePointRight(2).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private void setIfPresent(JsonNode node, String fieldName, java.util.function.Consumer<String> setter) {
        JsonNode value = node.get(fieldName);
        if (value != null && !value.asText().isBlank()) {
            setter.accept(value.asText());
        }
    }

    private void setGoodsIdIfMissing(JsonNode node, String fieldName, OrderDetailResult result) {
        if (result.getXyGoodsId() != null && !result.getXyGoodsId().isBlank()) {
            return;
        }
        setIfPresent(node, fieldName, result::setXyGoodsId);
    }

    private String text(JsonNode node) {
        return node == null ? "" : node.asText("");
    }

    private String extractCityFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        String normalized = normalizeAddressForCity(address);
        String municipality = normalizeMunicipality(normalized);
        if (municipality != null) {
            return municipality;
        }

        int cityEnd = normalized.indexOf('市');
        if (cityEnd < 1) {
            return null;
        }

        int cityStart = 0;
        int provinceEnd = normalized.lastIndexOf('省', cityEnd);
        int autonomousRegionEnd = normalized.lastIndexOf("自治区", cityEnd);
        if (provinceEnd >= 0) {
            cityStart = provinceEnd + 1;
        } else if (autonomousRegionEnd >= 0) {
            cityStart = autonomousRegionEnd + "自治区".length();
        }

        String city = normalized.substring(cityStart, cityEnd + 1);
        return city.length() > 1 ? city : null;
    }

    private String normalizeAddressForCity(String address) {
        String normalized = address.replaceAll("\\s+", "");
        int municipalityStart = firstMunicipalityIndex(normalized);
        if (municipalityStart >= 0) {
            return normalized.substring(municipalityStart);
        }
        return normalized;
    }

    private int firstMunicipalityIndex(String text) {
        int first = -1;
        for (String municipality : List.of("北京", "上海", "天津", "重庆")) {
            int index = text.indexOf(municipality);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
    }

    private String normalizeMunicipality(String text) {
        if (text.startsWith("北京")) {
            return "北京市";
        }
        if (text.startsWith("上海")) {
            return "上海市";
        }
        if (text.startsWith("天津")) {
            return "天津市";
        }
        if (text.startsWith("重庆")) {
            return "重庆市";
        }
        return null;
    }
}
