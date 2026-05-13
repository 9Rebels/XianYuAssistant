package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.SoldOrderSyncService;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SoldOrderSyncServiceImpl implements SoldOrderSyncService {
    private static final String API_NAME = "mtop.taobao.idle.trade.merchant.sold.get";
    private static final String API_VERSION = "1.0";
    private static final String APP_KEY = "34839810";
    private static final String SELLER_ORIGIN = "https://seller.goofish.com";
    private static final String SELLER_REFERER = "https://seller.goofish.com/";
    private static final String SPM_CNT = "a21ybx.home.0.0";
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGES = 20;
    private static final DateTimeFormatter ORDER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private XianyuOrderMapper orderMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public SyncResult syncSoldOrders(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("账号ID不能为空");
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (!Boolean.TRUE.equals(account.getFishShopUser())) {
            throw new IllegalStateException(NON_FISH_SHOP_MESSAGE);
        }

        XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
        if (cookie == null || isBlank(cookie.getCookieText())) {
            throw new IllegalStateException("账号Cookie不存在");
        }

        log.info("开始同步鱼小铺卖家订单: accountId={}", accountId);
        SyncResult totalResult = new SyncResult();
        String lastEndRow = "0";
        int pageNumber = 1;

        while (pageNumber <= MAX_PAGES) {
            String response = requestSoldOrderPage(account, cookie.getCookieText(), pageNumber, lastEndRow);
            PageSaveResult pageResult = saveOrdersFromResponse(account, response);
            mergeResult(totalResult, pageResult);
            totalResult.setPageCount(pageNumber);
            totalResult.setTotalCount(pageResult.totalCount());
            totalResult.setNextPage(pageResult.nextPage());
            totalResult.setLastEndRow(pageResult.lastEndRow());

            if (!pageResult.nextPage() || pageResult.itemCount() == 0) {
                break;
            }
            lastEndRow = pageResult.lastEndRow() == null ? lastEndRow : pageResult.lastEndRow();
            pageNumber++;
        }

        log.info("鱼小铺卖家订单同步完成: accountId={}, fetched={}, inserted={}, updated={}",
                accountId, totalResult.getFetchedCount(), totalResult.getInsertedCount(),
                totalResult.getUpdatedCount());
        return totalResult;
    }

    private String requestSoldOrderPage(XianyuAccount account, String cookieText, int pageNumber, String lastEndRow) {
        try {
            String dataJson = objectMapper.writeValueAsString(buildPageData(account, pageNumber, lastEndRow));
            String timestamp = String.valueOf(System.currentTimeMillis());
            String token = XianyuSignUtils.extractToken(XianyuSignUtils.parseCookies(cookieText));
            if (isBlank(token)) {
                throw new IllegalStateException("Cookie中缺少_m_h5_tk，请重新登录");
            }
            String sign = XianyuSignUtils.generateSign(timestamp, token, dataJson);
            String url = buildUrl(timestamp, sign);
            String formBody = "data=" + encode(dataJson);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .headers(buildHeaders(cookieText))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("卖家订单接口HTTP异常: " + response.statusCode());
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("请求卖家订单接口失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildPageData(XianyuAccount account, int pageNumber, String lastEndRow) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("needGroupInfo", Boolean.TRUE);
        data.put("pageNumber", pageNumber);
        data.put("pageSize", PAGE_SIZE);
        data.put("lastEndRow", isBlank(lastEndRow) ? "0" : lastEndRow);
        data.put("userId", account == null ? null : account.getUnb());
        return data;
    }

    private String[] buildHeaders(String cookieText) {
        return new String[]{
                "Accept", "application/json",
                "Accept-Language", "zh-CN,zh;q=0.9",
                "Cache-Control", "no-cache",
                "Content-Type", "application/x-www-form-urlencoded",
                "Cookie", cookieText,
                "Origin", SELLER_ORIGIN,
                "Pragma", "no-cache",
                "Referer", SELLER_REFERER,
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
                "idle_site_biz_code", "COMMONPRO",
                "idle_user_group_member_id", ""
        };
    }

    private String buildUrl(String timestamp, String sign) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", APP_KEY);
        params.put("t", timestamp);
        params.put("sign", sign);
        params.put("v", API_VERSION);
        params.put("type", "json");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", API_NAME);
        params.put("valueType", "string");
        params.put("sessionOption", "AutoLoginOnly");
        params.put("spm_cnt", SPM_CNT);

        StringBuilder url = new StringBuilder("https://h5api.m.goofish.com/h5/");
        url.append(API_NAME).append("/").append(API_VERSION).append("/?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                url.append('&');
            }
            url.append(entry.getKey()).append('=').append(encode(entry.getValue()));
            first = false;
        }
        return url.toString();
    }

    PageSaveResult saveOrdersFromResponse(XianyuAccount account, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String retText = retText(root);
            if (!retText.contains("SUCCESS")) {
                throw new IllegalStateException("卖家订单接口返回异常: " + retText);
            }
            JsonNode module = root.path("data").path("module");
            JsonNode items = module.path("items");
            if (!items.isArray()) {
                throw new IllegalStateException("卖家订单接口响应缺少订单列表");
            }

            int inserted = 0;
            int updated = 0;
            int itemCount = 0;
            for (JsonNode item : items) {
                if (isBlank(text(item.path("commonData").path("orderId")))) {
                    continue;
                }
                boolean wasInserted = upsertOrder(account, item);
                if (wasInserted) {
                    inserted++;
                } else {
                    updated++;
                }
                itemCount++;
            }

            return new PageSaveResult(
                    itemCount,
                    inserted,
                    updated,
                    toInteger(text(module.path("totalCount"))),
                    Boolean.parseBoolean(text(module.path("nextPage"))),
                    text(module.path("lastEndRow"))
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析卖家订单响应失败: " + e.getMessage(), e);
        }
    }

    private boolean upsertOrder(XianyuAccount account, JsonNode item) throws Exception {
        JsonNode commonData = item.path("commonData");
        String orderId = text(commonData.path("orderId"));
        XianyuOrder order = orderMapper.selectOne(new LambdaQueryWrapper<XianyuOrder>()
                .eq(XianyuOrder::getXianyuAccountId, account.getId())
                .eq(XianyuOrder::getOrderId, orderId));
        boolean inserted = order == null;
        if (inserted) {
            order = new XianyuOrder();
            order.setXianyuAccountId(account.getId());
            order.setOrderId(orderId);
        }

        applyOrderFields(order, account, item);
        order.setCompleteMsg(objectMapper.writeValueAsString(item));
        if (inserted) {
            orderMapper.insert(order);
        } else {
            orderMapper.updateById(order);
        }
        return inserted;
    }

    private void applyOrderFields(XianyuOrder order, XianyuAccount account, JsonNode item) {
        JsonNode commonData = item.path("commonData");
        JsonNode buyerInfo = item.path("buyerInfoVO");
        JsonNode itemInfo = item.path("itemVO");
        JsonNode price = item.path("priceVO");

        setIfPresent(text(commonData.path("itemId")), order::setXyGoodsId);
        setIfPresent(text(itemInfo.path("title")), order::setGoodsTitle);
        setIfPresent(text(buyerInfo.path("buyerId")), order::setBuyerUserId);
        setIfPresent(text(buyerInfo.path("userNick")), order::setBuyerUserName);
        setIfPresent(account.getUnb(), order::setSellerUserId);
        setIfPresent(account.getDisplayName(), order::setSellerUserName);
        setIfPresent(text(buyerInfo.path("name")), order::setReceiverName);
        setIfPresent(text(buyerInfo.path("phone")), order::setReceiverPhone);
        setIfPresent(text(buyerInfo.path("address")), order::setReceiverAddress);

        String address = text(buyerInfo.path("address"));
        if (!isBlank(address) && isBlank(order.getReceiverCity())) {
            order.setReceiverCity(extractCityFromAddress(address));
        }

        String statusText = text(commonData.path("orderStatus"));
        setIfPresent(statusText, order::setOrderStatusText);
        Integer status = mapOrderStatus(statusText);
        if (status != null) {
            order.setOrderStatus(status);
        }

        String amountText = firstNonBlank(text(price.path("totalPrice")), text(price.path("confirmFee")));
        setIfPresent(amountText, order::setOrderAmountText);
        Long amount = toCent(amountText);
        if (amount != null) {
            order.setOrderAmount(amount);
        }

        setIfPresent(parseOrderTime(text(commonData.path("createTime"))), order::setOrderCreateTime);
        setIfPresent(parseOrderTime(text(commonData.path("paySuccessTime"))), order::setOrderPayTime);
        setIfPresent(parseOrderTime(text(commonData.path("consignTime"))), order::setOrderDeliveryTime);
        setIfPresent(parseOrderTime(text(commonData.path("finishTime"))), order::setOrderCompleteTime);
    }

    private Integer mapOrderStatus(String statusText) {
        if (isBlank(statusText)) {
            return null;
        }
        if (statusText.contains("待付款") || statusText.contains("等待买家付款")) {
            return 1;
        }
        if (statusText.contains("待发货") || statusText.contains("未发货")) {
            return 2;
        }
        if (statusText.contains("已发货")) {
            return 3;
        }
        if (statusText.contains("交易成功") || statusText.contains("已完成")) {
            return 4;
        }
        if (statusText.contains("关闭") || statusText.contains("取消")) {
            return 5;
        }
        return null;
    }

    private Long parseOrderTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), ORDER_TIME_FORMATTER)
                    .atZone(DEFAULT_ZONE)
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            log.debug("解析卖家订单时间失败: {}", value);
            return null;
        }
    }

    private Long toCent(String price) {
        if (isBlank(price)) {
            return null;
        }
        try {
            return new BigDecimal(price.trim()).movePointRight(2).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractCityFromAddress(String address) {
        if (isBlank(address)) {
            return null;
        }
        String normalized = address.replaceAll("\\s+", "");
        for (String city : List.of("北京市", "上海市", "天津市", "重庆市")) {
            if (normalized.contains(city.substring(0, 2))) {
                return city;
            }
        }
        int cityEnd = normalized.indexOf('市');
        if (cityEnd < 1) {
            return null;
        }
        int provinceEnd = normalized.lastIndexOf('省', cityEnd);
        int cityStart = provinceEnd >= 0 ? provinceEnd + 1 : 0;
        return normalized.substring(cityStart, cityEnd + 1);
    }

    private void mergeResult(SyncResult totalResult, PageSaveResult pageResult) {
        totalResult.setFetchedCount(totalResult.getFetchedCount() + pageResult.itemCount());
        totalResult.setInsertedCount(totalResult.getInsertedCount() + pageResult.insertedCount());
        totalResult.setUpdatedCount(totalResult.getUpdatedCount() + pageResult.updatedCount());
    }

    private String retText(JsonNode root) {
        JsonNode ret = root.path("ret");
        if (ret.isArray() && !ret.isEmpty()) {
            return ret.get(0).asText("");
        }
        return ret.asText("");
    }

    private Integer toInteger(String value) {
        try {
            return isBlank(value) ? null : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private <T> void setIfPresent(T value, java.util.function.Consumer<T> setter) {
        if (value instanceof String text) {
            if (!isBlank(text)) {
                setter.accept(value);
            }
            return;
        }
        if (value != null) {
            setter.accept(value);
        }
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record PageSaveResult(int itemCount, int insertedCount, int updatedCount,
                          Integer totalCount, boolean nextPage, String lastEndRow) {
    }
}
