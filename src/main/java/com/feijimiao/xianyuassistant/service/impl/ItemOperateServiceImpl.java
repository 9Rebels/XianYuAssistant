package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.feijimiao.xianyuassistant.controller.dto.ItemDTO;
import com.feijimiao.xianyuassistant.controller.dto.ItemListRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.service.ItemOperateService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ItemOperateServiceImpl implements ItemOperateService {
    private static final int STATUS_ON_SALE = 0;
    private static final int STATUS_SOLD = 2;
    private static final String NORMAL_OFF_SHELF_API = "mtop.taobao.idle.item.downshelf";
    private static final String NORMAL_OFF_SHELF_VERSION = "2.0";
    private static final String NORMAL_DELETE_API = "com.taobao.idle.item.delete";
    private static final String NORMAL_DELETE_VERSION = "1.1";
    private static final String EDIT_DETAIL_API = "mtop.idle.pc.idleitem.editDetail";
    private static final String EDIT_API = "mtop.idle.pc.idleitem.edit";
    private static final String PUBLISH_SPM_CNT = "a21ybx.publish.0.0";
    private static final String PUBLISH_SPM_PRE = "a21ybx.personal.sidebar.1.32336ac2lwic8T";
    private static final String PUBLISH_SOURCE_ID = "pcMainPublish";
    private static final String SELLER_SEARCH_API = "mtop.alibaba.idle.seller.pc.common.item.search";
    private static final String SELLER_UPDATE_API = "mtop.alibaba.idle.seller.pc.item.info.update";
    private static final String SELLER_OFF_SHELF_API = "mtop.alibaba.idle.seller.pc.item.offline";
    private static final String SELLER_DELETE_API = "mtop.alibaba.idle.seller.pc.item.delete";
    private static final String SELLER_API_VERSION = "1.0";
    private static final String APP_KEY = "34839810";
    private static final String SELLER_ORIGIN = "https://seller.goofish.com";
    private static final String SELLER_REFERER = "https://seller.goofish.com/";
    private static final String SELLER_SPM_CNT = "a21ybx.item.0.0";
    private static final int SELLER_SEARCH_PAGE_SIZE = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    @Override
    public ItemListRespDTO getSellerItemList(String cookie, int pageNumber, int pageSize, int syncStatus) {
        String response = callSellerApi(SELLER_SEARCH_API, SELLER_API_VERSION,
                sellerListPayload(pageNumber, pageSize, syncStatus), cookie);
        return parseSellerItemList(response, pageNumber, pageSize, syncStatus);
    }

    @Override
    public void offShelfItem(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo) {
        if (isFishShop(account)) {
            callSellerApi(SELLER_OFF_SHELF_API, SELLER_API_VERSION, itemIdData(goodsInfo), cookie);
            return;
        }
        callNormalApi(account, cookie, NORMAL_OFF_SHELF_API, NORMAL_OFF_SHELF_VERSION,
                itemIdData(goodsInfo), "普通账号商品下架");
    }

    @Override
    public void upShelfItem(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo) {
        String editDetailResponse = callNormalApi(account, cookie, EDIT_DETAIL_API, SELLER_API_VERSION,
                itemIdData(goodsInfo), "恢复原商品编辑信息", PUBLISH_SPM_CNT, PUBLISH_SPM_PRE);
        Map<String, Object> editData = buildEditPayload(editDetailResponse, goodsInfo);
        callNormalApi(account, cookie, EDIT_API, SELLER_API_VERSION,
                editData, "恢复原商品在售", PUBLISH_SPM_CNT, PUBLISH_SPM_PRE);
    }

    @Override
    public void deleteItem(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo) {
        if (isFishShop(account)) {
            Map<String, Object> data = itemIdData(goodsInfo);
            data.put("draftId", null);
            callSellerApi(SELLER_DELETE_API, SELLER_API_VERSION, data, cookie);
            return;
        }
        callNormalApi(account, cookie, NORMAL_DELETE_API, NORMAL_DELETE_VERSION,
                itemIdData(goodsInfo), "普通账号商品删除");
    }

    @Override
    public void updatePrice(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo, String price) {
        if (!isFishShop(account)) {
            throw new IllegalStateException("当前账号不是鱼小铺，无法改价");
        }
        JsonNode sellerItem = findSellerItem(goodsInfo.getXyGoodId(), cookie);
        Map<String, Object> data = buildSellerPriceUpdatePayload(sellerItem, price);
        callSellerApi(SELLER_UPDATE_API, SELLER_API_VERSION, data, cookie);
    }

    @Override
    public void updateQuantity(XianyuAccount account, String cookie, XianyuGoodsInfo goodsInfo, Integer quantity) {
        if (!isFishShop(account)) {
            throw new IllegalStateException("普通账号暂未找到可用库存修改接口，无法调整库存");
        }
        JsonNode sellerItem = findSellerItem(goodsInfo.getXyGoodId(), cookie);
        Map<String, Object> data = buildSellerQuantityUpdatePayload(
                sellerItem, quantity, goodsInfo != null ? goodsInfo.getSoldPrice() : null);
        callSellerApi(SELLER_UPDATE_API, SELLER_API_VERSION, data, cookie);
    }

    Map<String, Object> buildSellerPriceUpdatePayload(JsonNode sellerItem, String price) {
        String itemId = text(sellerItem.path("itemId"));
        if (isBlank(itemId)) {
            throw new IllegalStateException("卖家商品数据缺少商品ID");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId);
        JsonNode skuList = sellerItem.path("idleItemSkuList");
        if (skuList.isArray() && !skuList.isEmpty()) {
            data.put("itemSkuListStr", buildSkuListStr(skuList, price));
            return data;
        }

        data.put("quantity", readQuantity(sellerItem.path("quantity")));
        data.put("price", price);
        return data;
    }

    Map<String, Object> buildSellerQuantityUpdatePayload(JsonNode sellerItem, Integer quantity, String fallbackPrice) {
        int normalizedQuantity = normalizeQuantity(quantity);
        String itemId = text(sellerItem.path("itemId"));
        if (isBlank(itemId)) {
            throw new IllegalStateException("卖家商品数据缺少商品ID");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId);
        JsonNode skuList = sellerItem.path("idleItemSkuList");
        if (skuList.isArray() && !skuList.isEmpty()) {
            data.put("itemSkuListStr", buildSkuListQuantityStr(skuList, normalizedQuantity));
            return data;
        }

        data.put("quantity", normalizedQuantity);
        data.put("price", resolveQuantityPrice(sellerItem.path("price"), fallbackPrice));
        return data;
    }

    private String buildSkuListStr(JsonNode skuList, String price) {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode sku : skuList) {
                String skuId = text(sku.path("skuId"));
                if (isBlank(skuId)) {
                    throw new IllegalStateException("卖家商品SKU缺少skuId");
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skuId", skuId);
                item.put("quantity", readQuantity(sku.path("quantity")));
                item.put("price", price);
                items.add(item);
            }
            return objectMapper.writeValueAsString(items);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建鱼小铺SKU改价参数失败: " + e.getMessage(), e);
        }
    }

    private String buildSkuListQuantityStr(JsonNode skuList, int quantity) {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode sku : skuList) {
                String skuId = text(sku.path("skuId"));
                if (isBlank(skuId)) {
                    throw new IllegalStateException("卖家商品SKU缺少skuId");
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("skuId", skuId);
                item.put("quantity", quantity);
                item.put("price", readRequiredPrice(sku.path("price")));
                items.add(item);
            }
            return objectMapper.writeValueAsString(items);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建鱼小铺SKU库存参数失败: " + e.getMessage(), e);
        }
    }

    private JsonNode findSellerItem(String itemId, String cookie) {
        for (Map<String, Object> data : sellerSearchPayloads(itemId)) {
            String response = callSellerApi(SELLER_SEARCH_API, SELLER_API_VERSION, data, cookie);
            JsonNode item = firstSellerSearchItem(response, itemId);
            if (!item.isMissingNode()) {
                return item;
            }
        }
        throw new IllegalStateException("鱼小铺工作台未找到该商品，无法改价");
    }

    private List<Map<String, Object>> sellerSearchPayloads(String itemId) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        payloads.add(sellerSearchPayload(itemId, null));
        payloads.add(sellerSearchPayload(itemId, "0,-9"));
        payloads.add(sellerSearchPayload(itemId, "1"));
        return payloads;
    }

    private Map<String, Object> sellerSearchPayload(String itemId, String itemStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNo", 1);
        data.put("pageSize", SELLER_SEARCH_PAGE_SIZE);
        data.put("bizType", "commonPro");
        data.put("searchRequest", "{\"itemId\":\"" + itemId + "\"}");
        if (!isBlank(itemStatus)) {
            data.put("itemStatus", itemStatus);
        }
        return data;
    }

    private Map<String, Object> sellerListPayload(int pageNumber, int pageSize, int syncStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pageNo", Math.max(pageNumber, 1));
        data.put("pageSize", Math.max(pageSize, 1));
        data.put("bizType", "commonPro");
        String itemStatus = sellerListItemStatus(syncStatus);
        if (!isBlank(itemStatus)) {
            data.put("itemStatus", itemStatus);
        }
        return data;
    }

    private String sellerListItemStatus(int syncStatus) {
        if (syncStatus == STATUS_ON_SALE) {
            return "0,-9";
        }
        if (syncStatus == STATUS_SOLD) {
            return "2";
        }
        return String.valueOf(syncStatus);
    }

    private ItemListRespDTO parseSellerItemList(String response, int pageNumber, int pageSize, int syncStatus) {
        try {
            JsonNode items = objectMapper.readTree(response)
                    .path("data").path("data").path("itemSearchResponseList");
            ItemListRespDTO respDTO = new ItemListRespDTO();
            respDTO.setSuccess(true);
            respDTO.setPageNumber(pageNumber);
            respDTO.setPageSize(pageSize);
            respDTO.setItems(new ArrayList<>());

            if (items.isArray()) {
                for (JsonNode item : items) {
                    respDTO.getItems().add(toItemDTO(item, syncStatus));
                }
            }

            respDTO.setCurrentCount(respDTO.getItems().size());
            respDTO.setSavedCount(respDTO.getItems().size());
            return respDTO;
        } catch (Exception e) {
            throw new IllegalStateException("解析鱼小铺商品列表失败: " + e.getMessage(), e);
        }
    }

    private ItemDTO toItemDTO(JsonNode sellerItem, int syncStatus) {
        ItemDTO item = new ItemDTO();
        String itemId = text(sellerItem.path("itemId"));
        item.setId(itemId);
        item.setTitle(firstText(sellerItem, "title", "itemTitle"));
        item.setItemStatus(syncStatus);
        item.setQuantity(readOptionalQuantity(sellerItem));
        item.setDetailUrl("https://www.goofish.com/item?id=" + itemId);

        ItemDTO.PriceInfo priceInfo = new ItemDTO.PriceInfo();
        priceInfo.setPrice(firstText(sellerItem, "price", "reservePrice", "soldPrice"));
        item.setPriceInfo(priceInfo);

        ItemDTO.DetailParams detailParams = new ItemDTO.DetailParams();
        detailParams.setItemId(itemId);
        detailParams.setTitle(item.getTitle());
        detailParams.setPicUrl(firstText(sellerItem, "picUrl", "itemPicUrl", "mainPicUrl"));
        detailParams.setSoldPrice(priceInfo.getPrice());
        item.setDetailParams(detailParams);

        ItemDTO.PicInfo picInfo = new ItemDTO.PicInfo();
        picInfo.setPicUrl(detailParams.getPicUrl());
        item.setPicInfo(picInfo);
        return item;
    }

    private JsonNode firstSellerSearchItem(String response, String itemId) {
        try {
            JsonNode items = objectMapper.readTree(response)
                    .path("data").path("data").path("itemSearchResponseList");
            if (!items.isArray()) {
                return MissingNode.getInstance();
            }
            for (JsonNode item : items) {
                if (itemId.equals(text(item.path("itemId")))) {
                    return item;
                }
            }
            return MissingNode.getInstance();
        } catch (Exception e) {
            throw new IllegalStateException("解析鱼小铺商品列表失败: " + e.getMessage(), e);
        }
    }

    private String callNormalApi(XianyuAccount account, String cookie, String apiName, String version,
                                 Map<String, Object> data, String operationName) {
        return callNormalApi(account, cookie, apiName, version, data, operationName, null, null);
    }

    private String callNormalApi(XianyuAccount account, String cookie, String apiName, String version,
                                 Map<String, Object> data, String operationName,
                                 String spmCnt, String spmPre) {
        XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
        request.setAccountId(account.getId());
        request.setOperationName(operationName);
        request.setApiName(apiName);
        request.setVersion(version);
        request.setDataMap(data);
        request.setCookie(cookie);
        request.setSpmCnt(spmCnt);
        request.setSpmPre(spmPre);

        XianyuApiRecoveryResult result = xianyuApiRecoveryService.callApi(request);
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getErrorMessage() == null
                    ? "闲鱼商品操作失败"
                    : result.getErrorMessage());
        }
        return result.getResponse();
    }

    Map<String, Object> buildEditPayload(String editDetailResponse, XianyuGoodsInfo goodsInfo) {
        try {
            JsonNode data = objectMapper.readTree(editDetailResponse).path("data");
            JsonNode payloadNode = findEditPayloadNode(data);
            if (payloadNode.isMissingNode() || !payloadNode.isObject()) {
                throw new IllegalStateException("编辑详情缺少可提交商品数据");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(payloadNode, Map.class);
            Map<String, Object> dataMap = new LinkedHashMap<>(payload);
            dataMap.put("itemId", goodsInfo.getXyGoodId());
            dataMap.put("uniqueCode", String.valueOf(System.currentTimeMillis()));
            dataMap.put("sourceId", PUBLISH_SOURCE_ID);
            dataMap.put("bizcode", PUBLISH_SOURCE_ID);
            dataMap.put("publishScene", PUBLISH_SOURCE_ID);
            return dataMap;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建恢复上架参数失败: " + e.getMessage(), e);
        }
    }

    private JsonNode findEditPayloadNode(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return MissingNode.getInstance();
        }
        String[] candidateNames = {"itemInfo", "item", "itemDO", "idleItemDTO", "idleItem"};
        for (String name : candidateNames) {
            JsonNode candidate = data.path(name);
            if (candidate.isObject()) {
                return candidate;
            }
        }
        if (data.isObject()) {
            return data;
        }
        return MissingNode.getInstance();
    }

    private String callSellerApi(String apiName, String version, Map<String, Object> data, String cookie) {
        try {
            String dataJson = objectMapper.writeValueAsString(data);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String token = XianyuSignUtils.extractToken(XianyuSignUtils.parseCookies(cookie));
            if (isBlank(token)) {
                throw new IllegalStateException("Cookie中缺少_m_h5_tk，请重新登录");
            }
            String sign = XianyuSignUtils.generateSign(timestamp, token, dataJson);
            String formBody = "data=" + encode(dataJson);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildSellerUrl(apiName, version, timestamp, sign)))
                    .headers(buildSellerHeaders(cookie))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("鱼小铺商品接口HTTP异常: " + response.statusCode());
            }
            ensureSellerResponseSuccess(response.body());
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("请求鱼小铺商品接口失败: " + e.getMessage(), e);
        }
    }

    private void ensureSellerResponseSuccess(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String ret = root.path("ret").isArray() && !root.path("ret").isEmpty()
                    ? root.path("ret").get(0).asText("")
                    : "";
            if (!ret.contains("SUCCESS")) {
                throw new IllegalStateException(ret.isBlank() ? "鱼小铺商品接口返回异常" : ret);
            }
            JsonNode data = root.path("data");
            JsonNode innerData = data.path("data");
            if (innerData.isBoolean() && !innerData.asBoolean()) {
                String msg = text(data.path("msg"));
                throw new IllegalStateException(isBlank(msg) ? "鱼小铺商品操作失败" : msg);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析鱼小铺商品接口响应失败: " + e.getMessage(), e);
        }
    }

    private String buildSellerUrl(String apiName, String version, String timestamp, String sign) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", APP_KEY);
        params.put("t", timestamp);
        params.put("sign", sign);
        params.put("v", version);
        params.put("type", "json");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", apiName);
        params.put("sessionOption", "AutoLoginOnly");
        params.put("spm_cnt", SELLER_SPM_CNT);

        StringBuilder url = new StringBuilder("https://h5api.m.goofish.com/h5/");
        url.append(apiName).append('/').append(version).append("/?");
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

    private String[] buildSellerHeaders(String cookie) {
        return new String[]{
                "Accept", "application/json",
                "Accept-Language", "zh-CN,zh;q=0.9",
                "Cache-Control", "no-cache",
                "Content-Type", "application/x-www-form-urlencoded",
                "Cookie", cookie,
                "Origin", SELLER_ORIGIN,
                "Pragma", "no-cache",
                "Referer", SELLER_REFERER,
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
                "idle_site_biz_code", "COMMONPRO",
                "idle_user_group_member_id", ""
        };
    }

    private Map<String, Object> itemIdData(XianyuGoodsInfo goodsInfo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", goodsInfo.getXyGoodId());
        return data;
    }

    private Object readQuantity(JsonNode quantityNode) {
        if (quantityNode.isNumber()) {
            return quantityNode.asInt();
        }
        String quantity = text(quantityNode);
        if (isBlank(quantity)) {
            throw new IllegalStateException("卖家商品数据缺少库存，无法改价");
        }
        try {
            return Integer.parseInt(quantity);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("卖家商品库存不是整数，无法改价");
        }
    }

    private Integer readOptionalQuantity(JsonNode sellerItem) {
        Integer quantity = optionalQuantity(sellerItem.path("quantity"));
        if (quantity != null) {
            return quantity;
        }
        JsonNode skuList = sellerItem.path("idleItemSkuList");
        if (!skuList.isArray()) {
            return null;
        }
        int total = 0;
        boolean found = false;
        for (JsonNode sku : skuList) {
            Integer skuQuantity = optionalQuantity(sku.path("quantity"));
            if (skuQuantity != null) {
                total += skuQuantity;
                found = true;
            }
        }
        return found ? Math.max(total, 0) : null;
    }

    private Integer optionalQuantity(JsonNode quantityNode) {
        if (quantityNode == null || quantityNode.isMissingNode() || quantityNode.isNull()) {
            return null;
        }
        if (quantityNode.isNumber()) {
            return quantityNode.asInt();
        }
        String quantity = text(quantityNode);
        if (isBlank(quantity)) {
            return null;
        }
        try {
            return Integer.parseInt(quantity);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalStateException("库存必须大于0");
        }
        return quantity;
    }

    private String readRequiredPrice(JsonNode priceNode) {
        String price = text(priceNode);
        if (isBlank(price)) {
            throw new IllegalStateException("卖家商品数据缺少价格，无法调整库存");
        }
        return price;
    }

    private String resolveQuantityPrice(JsonNode priceNode, String fallbackPrice) {
        String price = text(priceNode);
        if (!isBlank(price)) {
            return price;
        }
        if (!isBlank(fallbackPrice)) {
            return fallbackPrice;
        }
        throw new IllegalStateException("卖家商品数据缺少价格，无法调整库存");
    }

    private boolean isFishShop(XianyuAccount account) {
        return account != null && Boolean.TRUE.equals(account.getFishShopUser());
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node.path(name));
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
