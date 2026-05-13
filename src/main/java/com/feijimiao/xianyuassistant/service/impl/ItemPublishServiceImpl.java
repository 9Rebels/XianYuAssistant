package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemAddrDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemCatDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemLabelDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemSkuDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemSpecDTO;
import com.feijimiao.xianyuassistant.controller.dto.PublishItemSpecValueDTO;
import com.feijimiao.xianyuassistant.entity.XianyuPublishSchedule;
import com.feijimiao.xianyuassistant.mapper.XianyuPublishScheduleMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.ItemPublishService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ItemPublishServiceImpl implements ItemPublishService {

    private static final String PUBLISH_API = "mtop.idle.pc.idleitem.publish";
    private static final String CATEGORY_RECOMMEND_API = "mtop.taobao.idle.kgraph.property.recommend";
    private static final String CATEGORY_RECOMMEND_VERSION = "2.0";
    private static final String PUBLISH_SPM_CNT = "a21ybx.publish.0.0";
    private static final String PUBLISH_SPM_PRE = "a21ybx.personal.sidebar.1.32336ac2lwic8T";
    private static final String CATEGORY_RECOMMEND_SPM_PRE = "a21ybx.item.sidebar.1.67321598K9Vgx8";
    private static final int MAX_TITLE_LENGTH = 30;
    private static final int MAX_DESC_LENGTH = 5000;
    private static final int MAX_SPEC_GROUPS = 2;
    private static final int MAX_SPEC_NAME_LENGTH = 4;
    private static final int MAX_SPEC_VALUE_LENGTH = 12;
    private static final int MAX_SKU_COUNT = 1500;
    private static final int MAX_SKU_QUANTITY = 9999;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SHIPPING_FREE = "FREE_SHIPPING";
    private static final String SHIPPING_DISTANCE = "CHARGE_SHIPPING";
    private static final String SHIPPING_FIXED = "FIXED_PRICE";
    private static final String SHIPPING_NONE = "NO_SHIPPING";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AccountService accountService;

    @Autowired
    private XianyuPublishScheduleMapper publishScheduleMapper;

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    @Override
    public ResultObject<PublishItemRespDTO> publishItem(PublishItemReqDTO reqDTO) {
        try {
            String error = validateRequest(reqDTO);
            if (error != null) {
                return ResultObject.failed(error);
            }

            if (Boolean.TRUE.equals(reqDTO.getScheduled())) {
                return schedulePublish(reqDTO);
            }

            return publishNow(reqDTO);
        } catch (Exception e) {
            log.error("发布商品异常: xianyuAccountId={}", reqDTO != null ? reqDTO.getXianyuAccountId() : null, e);
            return ResultObject.failed("发布商品异常: " + e.getMessage());
        }
    }

    private ResultObject<PublishItemRespDTO> publishNow(PublishItemReqDTO reqDTO) {
        try {
            reqDTO.setScheduled(false);
            reqDTO.setScheduledTime(null);

            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }
            ResolvedCategory resolvedCategory = resolvePublishCategory(reqDTO, cookie);
            if (!resolvedCategory.success()) {
                return resolvedCategory.recoveryResult() != null
                        ? ResultObject.success(buildRecoveryResponse(resolvedCategory.recoveryResult()))
                        : ResultObject.failed(resolvedCategory.message());
            }

            Map<String, Object> dataMap = buildPublishData(reqDTO, resolvedCategory);
            XianyuApiRecoveryResult apiResult = callPublishApi(
                    reqDTO.getXianyuAccountId(), "发布商品", PUBLISH_API, dataMap, cookie, PUBLISH_SPM_PRE, "1.0");
            if (!apiResult.isSuccess()) {
                return apiResult.getRecoveryResult() != null
                        ? ResultObject.success(buildRecoveryResponse(apiResult.getRecoveryResult()))
                        : ResultObject.failed(buildApiFailureMessage(apiResult, "请求闲鱼发布接口失败"));
            }

            return parsePublishResponse(apiResult.getResponse());
        } catch (Exception e) {
            log.error("发布商品异常: xianyuAccountId={}", reqDTO.getXianyuAccountId(), e);
            return ResultObject.failed("发布商品异常: " + e.getMessage());
        }
    }

    private ResultObject<PublishItemRespDTO> schedulePublish(PublishItemReqDTO reqDTO) throws Exception {
        LocalDateTime scheduledAt = parseScheduledTime(reqDTO.getScheduledTime());
        if (!scheduledAt.isAfter(LocalDateTime.now())) {
            return ResultObject.failed("定时发布时间必须晚于当前时间");
        }

        XianyuPublishSchedule task = new XianyuPublishSchedule();
        task.setXianyuAccountId(reqDTO.getXianyuAccountId());
        task.setTitle(reqDTO.getTitle().trim());
        task.setPayloadJson(objectMapper.writeValueAsString(reqDTO));
        task.setStatus(0);
        task.setScheduledTime(scheduledAt.format(DATE_TIME_FORMATTER));
        publishScheduleMapper.insert(task);

        PublishItemRespDTO respDTO = new PublishItemRespDTO();
        respDTO.setSuccess(true);
        respDTO.setMessage("已加入定时发布队列");
        respDTO.setScheduledTaskId(task.getId());
        respDTO.setScheduledTime(task.getScheduledTime());
        log.info("定时发布任务已创建: taskId={}, accountId={}, scheduledTime={}",
                task.getId(), task.getXianyuAccountId(), task.getScheduledTime());
        return ResultObject.success(respDTO);
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void executeDuePublishTasks() {
        List<XianyuPublishSchedule> tasks = publishScheduleMapper.selectDueTasks(5);
        for (XianyuPublishSchedule task : tasks) {
            executePublishTask(task);
        }
    }

    private void executePublishTask(XianyuPublishSchedule task) {
        try {
            task.setStatus(2);
            publishScheduleMapper.updateById(task);

            PublishItemReqDTO reqDTO = objectMapper.readValue(task.getPayloadJson(), PublishItemReqDTO.class);
            reqDTO.setScheduled(false);
            reqDTO.setScheduledTime(null);
            ResultObject<PublishItemRespDTO> result = publishNow(reqDTO);

            task.setExecutedTime(LocalDateTime.now().format(DATE_TIME_FORMATTER));
            if (result.getCode() != null && result.getCode() == 200 && result.getData() != null
                    && Boolean.TRUE.equals(result.getData().getSuccess())) {
                task.setStatus(1);
                task.setItemId(result.getData().getItemId());
                task.setFailReason(null);
                log.info("定时发布成功: taskId={}, itemId={}", task.getId(), task.getItemId());
            } else {
                task.setStatus(-1);
                task.setFailReason(result.getMsg());
                log.warn("定时发布失败: taskId={}, reason={}", task.getId(), result.getMsg());
            }
        } catch (Exception e) {
            task.setStatus(-1);
            task.setExecutedTime(LocalDateTime.now().format(DATE_TIME_FORMATTER));
            task.setFailReason(e.getMessage());
            log.error("定时发布异常: taskId={}", task.getId(), e);
        } finally {
            publishScheduleMapper.updateById(task);
        }
    }

    private String validateRequest(PublishItemReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null) {
            return "账号ID不能为空";
        }
        if (isBlank(reqDTO.getTitle())) {
            return "商品标题不能为空";
        }
        if (reqDTO.getTitle().trim().length() > MAX_TITLE_LENGTH) {
            return "商品标题不能超过30个字符";
        }
        if (isBlank(reqDTO.getDesc())) {
            return "商品描述不能为空";
        }
        if (reqDTO.getDesc().trim().length() > MAX_DESC_LENGTH) {
            return "商品描述不能超过5000个字符";
        }
        if (reqDTO.getImageUrls() == null || reqDTO.getImageUrls().isEmpty()) {
            return "至少需要上传一张商品图片";
        }
        if (reqDTO.getItemAddr() == null || isBlank(reqDTO.getItemAddr().getProv())
                || isBlank(reqDTO.getItemAddr().getCity())) {
            return "请填写省份和城市";
        }
        if (reqDTO.getItemAddr().getDivisionId() == null
                || reqDTO.getItemAddr().getDivisionId() <= 0) {
            return "请填写有效地区编码";
        }
        if (isBlank(reqDTO.getItemAddr().getPoiId()) || isBlank(reqDTO.getItemAddr().getGps())
                || isBlank(reqDTO.getItemAddr().getPoiName())) {
            return "请先设置宝贝所在地地图点位，已保留表单内容";
        }
        return validatePrice(reqDTO);
    }

    private String validatePrice(PublishItemReqDTO reqDTO) {
        try {
            BigDecimal price = toCent(reqDTO.getPrice());
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                return "价格必须大于0";
            }
            if (!isBlank(reqDTO.getOrigPrice())) {
                BigDecimal origPrice = toCent(reqDTO.getOrigPrice());
                if (origPrice.compareTo(price) < 0) {
                    return "原价不能低于售价";
                }
            }
            String shippingType = normalizeShippingType(reqDTO);
            if (SHIPPING_DISTANCE.equals(shippingType)) {
                return "按距离计费暂未开放，请选择包邮、一口价或无需邮寄";
            }
            if (SHIPPING_FIXED.equals(shippingType)) {
                BigDecimal postFee = toCent(reqDTO.getPostFee());
                if (postFee.compareTo(BigDecimal.ZERO) < 0) {
                    return "固定邮费不能小于0";
                }
            }
            return validateSku(reqDTO);
        } catch (Exception e) {
            return "价格格式不正确";
        }
    }

    private Map<String, Object> buildPublishData(PublishItemReqDTO reqDTO, ResolvedCategory resolvedCategory) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("freebies", false);
        data.put("itemTypeStr", "b");
        data.put("quantity", normalizeQuantity(reqDTO.getQuantity()));
        data.put("simpleItem", "true");
        data.put("imageInfoDOList", buildImageInfoList(reqDTO.getImageUrls()));
        data.put("itemTextDTO", buildItemText(reqDTO));
        data.put("itemLabelExtList", resolvedCategory.labels());
        data.put("itemProperties", buildItemProperties(reqDTO));
        data.put("itemPriceDTO", buildPrice(reqDTO));
        data.put("userRightsProtocols", buildUserRightsProtocols());
        data.put("itemPostFeeDTO", buildPostFee(reqDTO));
        data.put("itemAddrDTO", buildAddr(reqDTO.getItemAddr()));
        data.put("defaultPrice", false);
        data.put("itemCatDTO", buildCat(resolvedCategory.category()));
        data.put("uniqueCode", String.valueOf(System.currentTimeMillis()));
        data.put("sourceId", "pcMainPublish");
        data.put("bizcode", "pcMainPublish");
        data.put("publishScene", "pcMainPublish");
        data.put("itemSkuList", buildSkuList(reqDTO));
        return data;
    }

    private List<Map<String, Object>> buildItemProperties(PublishItemReqDTO reqDTO) {
        List<PublishItemSpecDTO> specs = reqDTO.getItemProperties();
        if (specs == null || specs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(specs.size(), MAX_SPEC_GROUPS); i++) {
            PublishItemSpecDTO spec = specs.get(i);
            if (spec == null || isBlank(spec.getPropertyName())) {
                continue;
            }
            List<Map<String, Object>> values = new ArrayList<>();
            if (spec.getPropertyValues() != null) {
                for (PublishItemSpecValueDTO value : spec.getPropertyValues()) {
                    if (value != null && !isBlank(value.getPropertyValue())) {
                        values.add(Map.of("propertyValue", value.getPropertyValue().trim()));
                    }
                }
            }
            if (!values.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("propertyName", spec.getPropertyName().trim());
                item.put("supportImage", i == 0 && Boolean.TRUE.equals(spec.getSupportImage()));
                item.put("propertyValues", values);
                result.add(item);
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildSkuList(PublishItemReqDTO reqDTO) {
        List<PublishItemSkuDTO> skus = reqDTO.getItemSkuList();
        if (skus == null || skus.isEmpty()) {
            return List.of(singleInventorySku(reqDTO));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (PublishItemSkuDTO sku : skus) {
            if (sku == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("priceInCent", toCent(valueOrDefault(sku.getPrice(), reqDTO.getPrice())).toPlainString());
            item.put("quantity", normalizeSkuQuantity(sku.getQuantity()));
            item.put("propertyList", buildSkuPropertyList(sku));
            result.add(item);
        }
        return result.isEmpty() ? List.of(singleInventorySku(reqDTO)) : result;
    }

    private Map<String, Object> singleInventorySku(PublishItemReqDTO reqDTO) {
        Map<String, Object> sku = new LinkedHashMap<>();
        sku.put("priceInCent", toCent(reqDTO.getPrice()).toPlainString());
        sku.put("quantity", normalizeSkuQuantity(reqDTO.getQuantity()));
        sku.put("propertyList", Collections.emptyList());
        return sku;
    }

    private List<Map<String, Object>> buildSkuPropertyList(PublishItemSkuDTO sku) {
        List<Map<String, Object>> propertyList = new ArrayList<>();
        if (!isBlank(sku.getPropertyKey()) && !isBlank(sku.getPropertyValue())) {
            propertyList.add(Map.of(
                    "propertyText", sku.getPropertyKey().trim(),
                    "valueText", sku.getPropertyValue().trim()));
        }
        if (!isBlank(sku.getSecondPropertyKey()) && !isBlank(sku.getSecondPropertyValue())) {
            propertyList.add(Map.of(
                    "propertyText", sku.getSecondPropertyKey().trim(),
                    "valueText", sku.getSecondPropertyValue().trim()));
        }
        return propertyList;
    }

    private String validateSku(PublishItemReqDTO reqDTO) {
        List<PublishItemSpecDTO> specs = reqDTO.getItemProperties();
        List<PublishItemSkuDTO> skus = reqDTO.getItemSkuList();
        if (specs == null || specs.isEmpty()) {
            return validateSingleQuantity(reqDTO.getQuantity());
        }
        if (specs.size() > MAX_SPEC_GROUPS) {
            return "规格类型最多2个";
        }
        int combinations = 1;
        for (PublishItemSpecDTO spec : specs) {
            if (spec == null || isBlank(spec.getPropertyName())) {
                return "请填写规格类型名称";
            }
            if (spec.getPropertyName().trim().length() > MAX_SPEC_NAME_LENGTH) {
                return "规格类型名称最多4个字";
            }
            List<PublishItemSpecValueDTO> values = spec.getPropertyValues();
            if (values == null || values.isEmpty()) {
                return "请填写规格值";
            }
            int valueCount = 0;
            for (PublishItemSpecValueDTO value : values) {
                if (value == null || isBlank(value.getPropertyValue())) {
                    continue;
                }
                if (value.getPropertyValue().trim().length() > MAX_SPEC_VALUE_LENGTH) {
                    return "规格值最多12个字";
                }
                valueCount++;
            }
            if (valueCount == 0) {
                return "请填写规格值";
            }
            combinations *= valueCount;
        }
        if (combinations > MAX_SKU_COUNT) {
            return "规格组合数量超过上限，请精简规格值";
        }
        if (skus == null || skus.size() != combinations) {
            return "规格明细不完整，请检查价格和库存";
        }
        boolean hasPositiveStock = false;
        for (PublishItemSkuDTO sku : skus) {
            if (sku == null) {
                return "规格明细不完整";
            }
            if (isBlank(sku.getPrice())) {
                return "请填写规格价格";
            }
            toCent(sku.getPrice());
            int quantity = parseQuantity(sku.getQuantity(), -1);
            if (quantity < 0 || quantity > MAX_SKU_QUANTITY) {
                return "规格库存必须在0到9999之间";
            }
            if (quantity > 0) {
                hasPositiveStock = true;
            }
        }
        return hasPositiveStock ? null : "至少需要一个规格库存大于0";
    }

    private String validateSingleQuantity(String quantity) {
        int parsed = parseQuantity(quantity, -1);
        if (parsed < 1 || parsed > MAX_SKU_QUANTITY) {
            return "库存必须在1到9999之间";
        }
        return null;
    }

    private ResolvedCategory resolvePublishCategory(PublishItemReqDTO reqDTO, String cookie) throws Exception {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("title", reqDTO.getTitle().trim());
        dataMap.put("lockCpv", false);
        dataMap.put("multiSKU", false);
        dataMap.put("publishScene", "mainPublish");
        dataMap.put("scene", "newPublishChoice");
        dataMap.put("description", reqDTO.getDesc().trim());
        dataMap.put("imageInfos", buildImageInfoList(reqDTO.getImageUrls()));
        dataMap.put("uniqueCode", String.valueOf(System.currentTimeMillis()));

        XianyuApiRecoveryResult apiResult = callPublishApi(
                reqDTO.getXianyuAccountId(), "发布类目推荐", CATEGORY_RECOMMEND_API,
                dataMap, cookie, CATEGORY_RECOMMEND_SPM_PRE, CATEGORY_RECOMMEND_VERSION);
        if (!apiResult.isSuccess()) {
            if (apiResult.getRecoveryResult() != null) {
                return ResolvedCategory.failed(apiResult.getRecoveryResult());
            }
            return ResolvedCategory.failed(buildApiFailureMessage(
                    apiResult, "闲鱼类目推荐失败，请稍后重试，已保留表单内容"));
        }

        String response = apiResult.getResponse();
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        List<String> ret = (List<String>) responseMap.get("ret");
        if (ret == null || ret.isEmpty() || !ret.get(0).contains("SUCCESS")) {
            String message = ret != null && !ret.isEmpty() ? ret.get(0) : "闲鱼类目推荐失败";
            log.warn("闲鱼类目推荐失败: ret={}, response={}", ret, truncate(response, 1200));
            return ResolvedCategory.failed(message + "，已保留表单内容");
        }

        Map<String, Object> data = asMap(responseMap.get("data"));
        Map<String, Object> predicted = findMapValue(data, "categoryPredictResult");
        PublishItemCatDTO category = toPublishCategory(predicted);
        if (category == null) {
            log.warn("闲鱼类目推荐未返回categoryPredictResult: response={}", truncate(response, 1200));
            return ResolvedCategory.failed("闲鱼未返回可发布类目，请换个标题或分类后重试，已保留表单内容");
        }

        List<Map<String, Object>> labels = buildRecommendLabels(data, category);
        log.info("发布商品类目推荐成功: xianyuAccountId={}, catId={}, channelCatId={}, tbCatId={}, labelCount={}",
                reqDTO.getXianyuAccountId(), category.getCatId(), category.getChannelCatId(),
                category.getTbCatId(), labels.size());
        return ResolvedCategory.success(category, labels);
    }

    private XianyuApiRecoveryResult callPublishApi(Long accountId, String operationName, String apiName,
                                                   Map<String, Object> dataMap, String cookie,
                                                   String spmPre, String version) {
        XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
        request.setAccountId(accountId);
        request.setOperationName(operationName);
        request.setApiName(apiName);
        request.setDataMap(dataMap);
        request.setCookie(cookie);
        request.setSpmCnt(PUBLISH_SPM_CNT);
        request.setSpmPre(spmPre);
        request.setVersion(version);
        return xianyuApiRecoveryService.callApi(request);
    }

    private List<Map<String, Object>> buildImageInfoList(List<String> imageUrls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String url = imageUrls.get(i);
            if (isBlank(url)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("extraInfo", Map.of("isH", "false", "isT", "false", "raw", "false"));
            item.put("isQrCode", false);
            item.put("url", url.trim());
            item.put("heightSize", 0);
            item.put("widthSize", 0);
            item.put("major", i == 0);
            item.put("type", 0);
            item.put("status", "done");
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> buildItemText(PublishItemReqDTO reqDTO) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("desc", reqDTO.getDesc().trim());
        text.put("title", reqDTO.getTitle().trim());
        text.put("titleDescSeparate", false);
        return text;
    }

    private Map<String, Object> buildPrice(PublishItemReqDTO reqDTO) {
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("priceInCent", toCent(reqDTO.getPrice()).toPlainString());
        if (!isBlank(reqDTO.getOrigPrice())) {
            price.put("origPriceInCent", toCent(reqDTO.getOrigPrice()).toPlainString());
        }
        return price;
    }

    private Map<String, Object> buildPostFee(PublishItemReqDTO reqDTO) {
        String shippingType = normalizeShippingType(reqDTO);
        boolean supportSelfPick = Boolean.TRUE.equals(reqDTO.getSupportSelfPick());
        Map<String, Object> fee = new LinkedHashMap<>();
        if (SHIPPING_DISTANCE.equals(shippingType)) {
            fee.put("canFreeShipping", false);
            fee.put("supportFreight", true);
            fee.put("onlyTakeSelf", supportSelfPick);
            fee.put("templateId", "-100");
            return fee;
        }
        if (SHIPPING_FIXED.equals(shippingType)) {
            fee.put("canFreeShipping", false);
            fee.put("supportFreight", true);
            fee.put("onlyTakeSelf", supportSelfPick);
            fee.put("templateId", "0");
            fee.put("postPriceInCent", toCent(reqDTO.getPostFee()).toPlainString());
            return fee;
        }
        if (SHIPPING_NONE.equals(shippingType)) {
            fee.put("canFreeShipping", false);
            fee.put("supportFreight", false);
            fee.put("onlyTakeSelf", supportSelfPick);
            fee.put("templateId", "0");
            return fee;
        }
        fee.put("canFreeShipping", true);
        fee.put("supportFreight", true);
        fee.put("onlyTakeSelf", supportSelfPick);
        return fee;
    }

    private Map<String, Object> buildCat(PublishItemCatDTO cat) {
        PublishItemCatDTO safeCat = defaultCatIfMissing(cat);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("catId", safeCat.getCatId());
        result.put("catName", safeCat.getCatName());
        result.put("channelCatId", safeCat.getChannelCatId());
        result.put("tbCatId", safeCat.getTbCatId());
        return result;
    }

    private PublishItemCatDTO defaultCatIfMissing(PublishItemCatDTO cat) {
        if (cat != null && !isBlank(cat.getCatId()) && !isBlank(cat.getChannelCatId())) {
            return cat;
        }
        PublishItemCatDTO defaultCat = new PublishItemCatDTO();
        defaultCat.setCatId("50025461");
        defaultCat.setCatName("软件安装包/序列号/激活码");
        defaultCat.setChannelCatId("201449620");
        defaultCat.setTbCatId("50003316");
        return defaultCat;
    }

    private Map<String, Object> buildAddr(PublishItemAddrDTO addr) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("area", addr != null ? valueOrEmpty(addr.getArea()) : "");
        result.put("city", addr != null ? valueOrEmpty(addr.getCity()) : "");
        result.put("divisionId", addr != null && addr.getDivisionId() != null ? addr.getDivisionId() : 0);
        result.put("gps", addr != null ? valueOrEmpty(addr.getGps()) : "");
        result.put("poiId", addr != null ? valueOrEmpty(addr.getPoiId()) : "");
        result.put("poiName", addr != null ? valueOrEmpty(addr.getPoiName()) : "");
        result.put("prov", addr != null ? valueOrEmpty(addr.getProv()) : "");
        return result;
    }

    private List<Map<String, Object>> buildLabels(PublishItemReqDTO reqDTO) {
        if (reqDTO.getItemLabels() == null || reqDTO.getItemLabels().isEmpty()) {
            return List.of(defaultCategoryLabel(defaultCatIfMissing(reqDTO.getItemCat())));
        }
        List<Map<String, Object>> labels = new ArrayList<>();
        for (PublishItemLabelDTO label : reqDTO.getItemLabels()) {
            labels.add(labelToMap(label));
        }
        return labels;
    }

    private List<Map<String, Object>> buildRecommendLabels(Map<String, Object> data, PublishItemCatDTO category) {
        List<Map<String, Object>> labels = new ArrayList<>();
        Object cardList = data != null ? data.get("cardList") : null;
        if (cardList instanceof List<?> cards) {
            for (Object card : cards) {
                Map<String, Object> cardMap = asMap(card);
                Map<String, Object> cardData = asMap(cardMap != null ? cardMap.get("cardData") : null);
                Object valuesList = cardData != null ? cardData.get("valuesList") : null;
                if (!(valuesList instanceof List<?> values)) {
                    continue;
                }
                for (Object value : values) {
                    Map<String, Object> valueMap = asMap(value);
                    if (valueMap == null || !Boolean.TRUE.equals(valueMap.get("isClicked"))) {
                        continue;
                    }
                    labels.add(recommendLabelToMap(cardData, valueMap));
                    break;
                }
            }
        }
        if (labels.isEmpty()) {
            labels.add(defaultCategoryLabel(category));
        }
        return labels;
    }

    private Map<String, Object> recommendLabelToMap(Map<String, Object> cardData, Map<String, Object> valueMap) {
        String propertyId = stringValue(cardData.get("propertyId"));
        String propertyName = stringValue(cardData.get("propertyName"));
        String channelCatId = stringValue(valueMap.get("channelCatId"));
        String catName = stringValue(valueMap.get("catName"));

        PublishItemLabelDTO label = new PublishItemLabelDTO();
        label.setChannelCateName(catName);
        label.setChannelCateId(channelCatId);
        label.setTbCatId(stringValue(valueMap.get("tbCatId")));
        label.setLabelType("common");
        label.setPropertyName(propertyName);
        label.setIsUserClick("1");
        label.setFrom("newPublishChoice");
        label.setPropertyId(propertyId);
        label.setLabelFrom("newPublish");
        label.setText(catName);
        label.setProperties(propertyId + "##" + propertyName + ":" + channelCatId + "##" + catName);
        return labelToMap(label);
    }

    private PublishItemCatDTO toPublishCategory(Map<String, Object> predicted) {
        if (predicted == null) {
            return null;
        }
        String catId = stringValue(predicted.get("catId"));
        String catName = stringValue(predicted.get("catName"));
        String channelCatId = stringValue(predicted.get("channelCatId"));
        String tbCatId = stringValue(predicted.get("tbCatId"));
        if (isBlank(catId) || isBlank(catName) || isBlank(channelCatId) || isBlank(tbCatId)) {
            return null;
        }
        PublishItemCatDTO category = new PublishItemCatDTO();
        category.setCatId(catId);
        category.setCatName(catName);
        category.setChannelCatId(channelCatId);
        category.setTbCatId(tbCatId);
        return category;
    }

    private Map<String, Object> defaultCategoryLabel(PublishItemCatDTO cat) {
        PublishItemLabelDTO label = new PublishItemLabelDTO();
        label.setPropertyName("分类");
        label.setText(cat.getCatName());
        label.setProperties("-10000##分类:" + cat.getChannelCatId() + "##" + cat.getCatName());
        label.setChannelCateId(cat.getChannelCatId());
        label.setTbCatId(cat.getTbCatId());
        label.setPropertyId("-10000");
        label.setChannelCateName(cat.getCatName());
        return labelToMap(label);
    }

    private Map<String, Object> labelToMap(PublishItemLabelDTO label) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("channelCateName", label.getChannelCateName());
        map.put("valueId", label.getValueId());
        map.put("channelCateId", label.getChannelCateId());
        map.put("valueName", label.getValueName());
        map.put("tbCatId", label.getTbCatId());
        map.put("subPropertyId", label.getSubPropertyId());
        map.put("labelType", valueOrDefault(label.getLabelType(), "common"));
        map.put("subValueId", label.getSubValueId());
        map.put("labelId", label.getLabelId());
        map.put("propertyName", label.getPropertyName());
        map.put("isUserClick", valueOrDefault(label.getIsUserClick(), "1"));
        map.put("isUserCancel", label.getIsUserCancel());
        map.put("from", valueOrDefault(label.getFrom(), "newPublishChoice"));
        map.put("propertyId", label.getPropertyId());
        map.put("labelFrom", valueOrDefault(label.getLabelFrom(), "newPublish"));
        map.put("text", label.getText());
        map.put("properties", label.getProperties());
        return map;
    }

    private List<Map<String, Object>> buildUserRightsProtocols() {
        return List.of(
                userRight("FAST_DELIVERY_48_HOUR"),
                userRight("FAST_DELIVERY_24_HOUR"),
                userRight("VIRTUAL_NONCONFORMITY_FREE_REFUND_SERVICE"),
                userRight("SKILL_PLAY_NO_MIND"));
    }

    private Map<String, Object> userRight(String serviceCode) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("enable", false);
        item.put("serviceCode", serviceCode);
        return item;
    }

    private ResultObject<PublishItemRespDTO> parsePublishResponse(String response) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        List<String> ret = (List<String>) responseMap.get("ret");
        if (ret == null || ret.isEmpty() || !ret.get(0).contains("SUCCESS")) {
            return ResultObject.failed(ret != null && !ret.isEmpty() ? ret.get(0) : "发布商品失败");
        }

        String itemId = findStringValue(responseMap, "itemId", "itemIdStr", "idleItemId");
        if (isBlank(itemId)) {
            String errorMessage = findStringValue(responseMap,
                    "errorMessage", "errorMsg", "displayMsg", "retMsg", "message", "msg");
            log.warn("发布接口未返回商品ID，ret={}, response={}", ret, truncate(response, 1200));
            return ResultObject.failed(!isBlank(errorMessage)
                    ? errorMessage
                    : "发布接口未返回商品ID，可能未发布成功，已保留表单内容");
        }

        PublishItemRespDTO respDTO = new PublishItemRespDTO();
        respDTO.setSuccess(true);
        respDTO.setMessage("商品发布成功");
        respDTO.setItemId(itemId);
        respDTO.setItemUrl(findStringValue(responseMap, "itemUrl", "detailUrl"));
        return ResultObject.success(respDTO);
    }

    private PublishItemRespDTO buildRecoveryResponse(CookieRecoveryResult recovery) {
        PublishItemRespDTO respDTO = new PublishItemRespDTO();
        respDTO.setSuccess(false);
        respDTO.setMessage(recovery != null && recovery.getMessage() != null
                ? recovery.getMessage()
                : "已尝试自动刷新和验证，仍失败，请人工更新Cookie或完成滑块验证");
        respDTO.setRecoveryAttempted(recovery != null && recovery.isAttempted());
        respDTO.setNeedCaptcha(recovery != null && recovery.isNeedCaptcha());
        respDTO.setNeedManual(recovery != null && recovery.isNeedManual());
        respDTO.setManualVerifyUrl(recovery != null ? recovery.getManualVerifyUrl() : null);
        respDTO.setCaptchaUrl(recovery != null ? recovery.getCaptchaUrl() : null);
        respDTO.setSessionId(recovery != null ? recovery.getSessionId() : null);
        return respDTO;
    }

    private String buildApiFailureMessage(XianyuApiRecoveryResult apiResult, String fallback) {
        if (apiResult == null || isBlank(apiResult.getErrorMessage())) {
            return fallback;
        }
        return apiResult.getErrorMessage();
    }

    private String findStringValue(Object node, String... keys) {
        if (node instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
            for (Object value : map.values()) {
                String found = findStringValue(value, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof List<?> list) {
            for (Object value : list) {
                String found = findStringValue(value, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Map<String, Object> findMapValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            Object value = map.get(key);
            Map<String, Object> foundMap = asMap(value);
            if (foundMap != null) {
                return foundMap;
            }
            for (Object child : map.values()) {
                Map<String, Object> found = findMapValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof List<?> list) {
            for (Object child : list) {
                Map<String, Object> found = findMapValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private BigDecimal toCent(String yuan) {
        if (isBlank(yuan)) {
            throw new IllegalArgumentException("price is blank");
        }
        return new BigDecimal(yuan.trim()).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private String normalizeQuantity(String quantity) {
        if (isBlank(quantity)) {
            return "1";
        }
        try {
            return String.valueOf(Math.max(1, Integer.parseInt(quantity.trim())));
        } catch (NumberFormatException e) {
            return "1";
        }
    }

    private String normalizeSkuQuantity(String quantity) {
        int parsed = parseQuantity(quantity, 1);
        return String.valueOf(Math.max(0, Math.min(MAX_SKU_QUANTITY, parsed)));
    }

    private int parseQuantity(String quantity, int fallback) {
        if (isBlank(quantity)) {
            return fallback;
        }
        try {
            return Integer.parseInt(quantity.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private LocalDateTime parseScheduledTime(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("定时发布时间不能为空");
        }
        String normalized = value.trim().replace('T', ' ');
        if (normalized.length() == 16) {
            normalized = normalized + ":00";
        }
        return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
    }

    private String normalizeShippingType(PublishItemReqDTO reqDTO) {
        if (!isBlank(reqDTO.getShippingType())) {
            return reqDTO.getShippingType().trim();
        }
        return Boolean.TRUE.equals(reqDTO.getFreeShipping()) ? SHIPPING_FREE : SHIPPING_FIXED;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ResolvedCategory(boolean success, String message, PublishItemCatDTO category,
                                    List<Map<String, Object>> labels,
                                    CookieRecoveryResult recoveryResult) {
        private static ResolvedCategory success(PublishItemCatDTO category, List<Map<String, Object>> labels) {
            return new ResolvedCategory(true, null, category, labels, null);
        }

        private static ResolvedCategory failed(String message) {
            return new ResolvedCategory(false, message, null, Collections.emptyList(), null);
        }

        private static ResolvedCategory failed(CookieRecoveryResult recoveryResult) {
            String message = recoveryResult != null
                    ? recoveryResult.getMessage()
                    : "已尝试自动刷新和验证，仍失败，请人工更新Cookie或完成滑块验证";
            return new ResolvedCategory(false, message, null, Collections.emptyList(), recoveryResult);
        }
    }
}
