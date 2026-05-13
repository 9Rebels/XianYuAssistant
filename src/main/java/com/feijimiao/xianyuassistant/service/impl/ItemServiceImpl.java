package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.*;
import com.feijimiao.xianyuassistant.exception.CookieRecoveryRequiredException;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.service.ItemService;
import com.feijimiao.xianyuassistant.service.ItemOperateService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import com.feijimiao.xianyuassistant.service.bo.SaveSettingReqBO;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import com.feijimiao.xianyuassistant.utils.XianyuApiUtils;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import com.feijimiao.xianyuassistant.utils.ItemDetailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 商品服务实现类
 */
@Slf4j
@Service
public class ItemServiceImpl implements ItemService {

    private static final int STATUS_ON_SALE = 0;
    private static final int STATUS_OFF_SHELF = 1;
    private static final int STATUS_SOLD = 2;
    private static final int STATUS_REMOTE_DELETED = 3;
    private static final String GROUP_ON_SALE = "在售";
    private static final String GROUP_SOLD = "已售出";
    private static final String DEFAULT_ON_SALE_GROUP_ID = "58877261";
    private static final String ITEM_LIST_API = "mtop.idle.web.xyh.item.list";
    private static final String ITEM_LIST_SPM_CNT = "a21ybx.im.0.0";
    private static final String ITEM_LIST_SPM_PRE = "a21ybx.collection.menu.1.272b5141NafCNK";
    private static final int SOLD_PAGE_DELAY_MIN_MS = 5000;
    private static final int SOLD_PAGE_DELAY_MAX_MS = 10000;
    private static final String GLOBAL_AI_REPLY_TEMPLATE_PREFIX = "global_ai_reply_template_";
    private static final int ITEM_LIST_MAX_RECOVERY_ATTEMPTS = 1;
    private static final String GOODS_OFF_SHELF_ENABLED_KEY = "goods_off_shelf_enabled";
    private static final String GOODS_DELETE_ENABLED_KEY = "goods_delete_enabled";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.AccountService accountService;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.GoodsInfoService goodsInfoService;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.AutoDeliveryService autoDeliveryService;

    @Autowired
    private com.feijimiao.xianyuassistant.service.ItemDetailSyncService itemDetailSyncService;

    @Autowired
    private com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;

    @Autowired
    private SysSettingService sysSettingService;

    @Autowired
    private com.feijimiao.xianyuassistant.service.CookieRecoveryService cookieRecoveryService;

    @Autowired
    private com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService xianyuApiRecoveryService;

    @Autowired
    private com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper accountMapper;

    @Autowired
    private ItemOperateService itemOperateService;

    /**
     * 获取指定页的商品信息（内部方法）
     */
    private ResultObject<ItemListRespDTO> getItemList(ItemListReqDTO reqDTO) {
        return getItemList(reqDTO, 0);
    }

    private ResultObject<ItemListRespDTO> getItemList(ItemListReqDTO reqDTO, int recoveryAttempts) {
        try {
            log.info("开始获取商品列表: {}", reqDTO);

            // 从数据库获取Cookie
            String cookiesStr = getCookieFromDb(reqDTO.getCookieId());
            if (cookiesStr == null || cookiesStr.isEmpty()) {
                Long accountId = getAccountIdFromCookieId(reqDTO.getCookieId());
                if (recoveryAttempts < ITEM_LIST_MAX_RECOVERY_ATTEMPTS && accountId != null) {
                    CookieRecoveryResult recovery = cookieRecoveryService.recover(
                            accountId, "商品同步", "未找到有效Cookie");
                    if (recovery.isSuccess()) {
                        return getItemList(reqDTO, recoveryAttempts + 1);
                    }
                    throw new CookieRecoveryRequiredException(recovery);
                }
                log.error("未找到账号Cookie: cookieId={}", reqDTO.getCookieId());
                return ResultObject.failed("未找到账号Cookie");
            }
            log.info("Cookie获取成功，长度: {}", cookiesStr.length());

            // 检查Cookie中是否包含必需的token
            Map<String, String> cookies = XianyuSignUtils.parseCookies(cookiesStr);
            if (!cookies.containsKey("_m_h5_tk") || cookies.get("_m_h5_tk").isEmpty()) {
                Long accountId = getAccountIdFromCookieId(reqDTO.getCookieId());
                if (recoveryAttempts < ITEM_LIST_MAX_RECOVERY_ATTEMPTS && accountId != null) {
                    CookieRecoveryResult recovery = cookieRecoveryService.recover(
                            accountId, "商品同步", "Cookie中缺少_m_h5_tk");
                    if (recovery.isSuccess()) {
                        return getItemList(reqDTO, recoveryAttempts + 1);
                    }
                    throw new CookieRecoveryRequiredException(recovery);
                }
                log.error("Cookie中缺少_m_h5_tk字段！请重新登录");
                return ResultObject.failed("Cookie中缺少_m_h5_tk，请重新登录");
            }
            
            // 构建请求数据
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("needGroupInfo", Boolean.TRUE.equals(reqDTO.getNeedGroupInfo()));
            dataMap.put("pageNumber", reqDTO.getPageNumber());
            dataMap.put("pageSize", reqDTO.getPageSize());
            dataMap.put("groupName", reqDTO.getGroupName());
            dataMap.put("groupId", reqDTO.getGroupId());
            dataMap.put("defaultGroup", Boolean.TRUE.equals(reqDTO.getDefaultGroup()));
            dataMap.put("userId", cookies.get("unb"));
            
            log.info("调用商品列表API: groupName={}, pageNumber={}, pageSize={}",
                    reqDTO.getGroupName(), reqDTO.getPageNumber(), reqDTO.getPageSize());
            
            Long accountId = getAccountIdFromCookieId(reqDTO.getCookieId());
            XianyuApiRecoveryResult apiResult = xianyuApiRecoveryService.callApi(
                    buildApiRequest(accountId, "商品同步", ITEM_LIST_API,
                            dataMap, cookiesStr, ITEM_LIST_SPM_CNT, ITEM_LIST_SPM_PRE, "1.0"));
            if (!apiResult.isSuccess()) {
                if (apiResult.getRecoveryResult() != null) {
                    throw new CookieRecoveryRequiredException(apiResult.getRecoveryResult());
                }
                log.error("API调用失败: {}", apiResult.getErrorMessage());
                return ResultObject.failed(apiResult.getErrorMessage() != null
                        ? apiResult.getErrorMessage()
                        : "请求闲鱼API失败");
            }
            String response = apiResult.getResponse();
            
            log.info("API调用成功，响应长度: {}", response.length());
            log.debug("API响应完整内容: {}", response);

            // 解析响应
            log.info("开始解析响应JSON...");
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            log.info("JSON解析成功，响应字段: {}", responseMap.keySet());
            
            ItemListRespDTO respDTO = parseItemListResponse(responseMap, reqDTO.getPageNumber(), reqDTO.getPageSize());
            log.info("响应解析完成，success={}, 商品数量={}", respDTO.getSuccess(), respDTO.getCurrentCount());
            
            if (respDTO.getSuccess()) {
                log.info("获取商品列表成功: cookieId={}, 商品数量={}", 
                        reqDTO.getCookieId(), respDTO.getCurrentCount());
                
                return ResultObject.success(respDTO);
            } else {
                log.error("获取商品列表失败: success=false");
                return ResultObject.failed("获取商品列表失败");
            }
        } catch (com.feijimiao.xianyuassistant.exception.BusinessException e) {
            log.error("业务异常: cookieId={}, message={}", reqDTO.getCookieId(), e.getMessage());
            Long accountId = getAccountIdFromCookieId(reqDTO.getCookieId());
            if (shouldRecoverFromError(e.getMessage()) && recoveryAttempts < ITEM_LIST_MAX_RECOVERY_ATTEMPTS && accountId != null) {
                CookieRecoveryResult recovery = cookieRecoveryService.recover(
                        accountId, "商品同步", e.getMessage());
                if (recovery.isSuccess()) {
                    return getItemList(reqDTO, recoveryAttempts + 1);
                }
                throw new CookieRecoveryRequiredException(recovery);
            }
            throw e;
        } catch (CookieRecoveryRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取商品列表异常: cookieId={}", reqDTO.getCookieId(), e);
            return ResultObject.failed("获取商品列表异常: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<RefreshItemsRespDTO> refreshItems(AllItemsReqDTO reqDTO) {
        try {
            int syncStatus = normalizeRequestedSyncStatus(reqDTO.getSyncStatus());
            String syncLabel = getSyncLabel(syncStatus);
            log.info("开始刷新商品数据: xianyuAccountId={}, syncLabel={}", reqDTO.getXianyuAccountId(), syncLabel);
            
            // 验证账号ID
            if (reqDTO.getXianyuAccountId() == null) {
                log.error("账号ID不能为空");
                return ResultObject.failed("账号ID不能为空");
            }
            
            // 根据账号ID获取Cookie
            String cookieStr = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("未找到账号Cookie: xianyuAccountId={}", reqDTO.getXianyuAccountId());
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }
            
            log.info("获取账号Cookie成功: xianyuAccountId={}", reqDTO.getXianyuAccountId());

            RefreshItemsRespDTO respDTO = new RefreshItemsRespDTO();
            respDTO.setSuccess(false);
            respDTO.setUpdatedItemIds(new ArrayList<>());
            respDTO.setSyncStatus(syncStatus);
            respDTO.setSyncLabel(syncLabel);

            int requestPageSize = reqDTO.getPageSize() != null && reqDTO.getPageSize() > 0
                    ? reqDTO.getPageSize() : DEFAULT_PAGE_SIZE;

            SyncGroup syncGroup = resolveSyncGroup(reqDTO.getXianyuAccountId(), cookieStr, syncStatus);
            log.info("商品同步分组: xianyuAccountId={}, syncLabel={}, groupId={}, defaultGroup={}",
                    reqDTO.getXianyuAccountId(), syncLabel, syncGroup.groupId(), syncGroup.defaultGroup());
            
            List<ItemDTO> allItems = new ArrayList<>();
            int pageNumber = 1;

            // 自动分页获取所有商品
            while (true) {
                // 检查是否达到最大页数（maxPages为null或0表示不限制）
                if (reqDTO.getMaxPages() != null && reqDTO.getMaxPages() > 0 && pageNumber > reqDTO.getMaxPages()) {
                    log.info("达到最大页数限制: {}", reqDTO.getMaxPages());
                    break;
                }

                // 获取当前页
                ItemListReqDTO pageReqDTO = new ItemListReqDTO();
                pageReqDTO.setCookieId(String.valueOf(reqDTO.getXianyuAccountId()));
                pageReqDTO.setPageNumber(pageNumber);
                pageReqDTO.setPageSize(requestPageSize);
                if (syncGroup != null) {
                    pageReqDTO.setGroupName(syncGroup.groupName());
                    pageReqDTO.setGroupId(syncGroup.groupId());
                    pageReqDTO.setDefaultGroup(syncGroup.defaultGroup());
                }
                pageReqDTO.setSyncStatus(syncStatus);

                ResultObject<ItemListRespDTO> pageResult = getItemList(pageReqDTO);
                
                if (pageResult.getCode() != 200 || pageResult.getData() == null || !pageResult.getData().getSuccess()) {
                    log.error("获取第{}页失败", pageNumber);
                    // 如果是第一页就失败了，返回错误
                    if (pageNumber == 1) {
                        return ResultObject.failed(pageResult.getMsg() != null ? pageResult.getMsg() : "获取商品列表失败");
                    }
                    // 如果不是第一页，继续处理已获取的数据
                    break;
                }

                ItemListRespDTO pageData = pageResult.getData();
                if (pageData.getItems() == null || pageData.getItems().isEmpty()) {
                    log.info("第{}页没有数据，刷新完成", pageNumber);
                    break;
                }

                allItems.addAll(pageData.getItems());
                pageData.getItems().forEach(item -> item.setItemStatus(
                        normalizeItemStatusForSync(syncStatus, item.getItemStatus())));
                log.info("{}第{}页获取到{}个商品", syncLabel, pageNumber, pageData.getItems().size());

                // 如果当前页商品数量少于页面大小，说明已经是最后一页
                if (pageData.getItems().size() < requestPageSize) {
                    log.info("第{}页商品数量({})少于页面大小({})，刷新完成", 
                            pageNumber, pageData.getItems().size(), requestPageSize);
                    break;
                }

                pageNumber++;
                
                delayBetweenPages(syncStatus);
            }

            respDTO.setTotalCount(allItems.size());
            
            Long accountId = reqDTO.getXianyuAccountId();
            int missingCount = goodsInfoService.moveMissingGoodsToStatus(
                    accountId, syncStatus, collectItemIds(allItems), STATUS_OFF_SHELF);
            respDTO.setRemovedCount(missingCount);

            if (!allItems.isEmpty()) {
                List<ItemDTO> changedItems = saveIncrementalGoods(allItems, accountId, syncStatus, respDTO);
                
                respDTO.setSuccessCount(respDTO.getUpdatedItemIds().size());
                respDTO.setSuccess(true);
                respDTO.setMessage(syncLabel + "增量同步成功");

                if (!changedItems.isEmpty()) {
                    String syncId = itemDetailSyncService.startSync(reqDTO.getXianyuAccountId(), changedItems);
                    respDTO.setSyncId(syncId);
                }
                
                log.info("刷新商品数据完成: xianyuAccountId={}, syncLabel={}, 总数={}, 变化={}, 移出={}, syncId={}",
                        reqDTO.getXianyuAccountId(), syncLabel, respDTO.getTotalCount(),
                        respDTO.getSuccessCount(), respDTO.getRemovedCount(), respDTO.getSyncId());
            } else {
                respDTO.setSuccessCount(0);
                respDTO.setSuccess(true);
                respDTO.setMessage(syncLabel + "没有获取到商品数据，已移出本地缺失商品");
                log.warn("刷新商品数据完成，但{}没有获取到任何商品，移出本地缺失商品{}个", syncLabel, missingCount);
            }

            return ResultObject.success(respDTO);
        } catch (CookieRecoveryRequiredException e) {
            log.warn("刷新商品数据需要人工恢复: xianyuAccountId={}, message={}",
                    reqDTO.getXianyuAccountId(), e.getMessage());
            return ResultObject.success(buildRecoveryRequiredResponse(reqDTO, e.getRecoveryResult()));
        } catch (Exception e) {
            log.error("刷新商品数据异常: xianyuAccountId={}", reqDTO.getXianyuAccountId(), e);
            return ResultObject.failed("刷新商品数据异常: " + e.getMessage());
        }
    }

    private List<ItemDTO> saveIncrementalGoods(
            List<ItemDTO> allItems, Long accountId, int syncStatus, RefreshItemsRespDTO respDTO) {
        List<ItemDTO> changedItems = new ArrayList<>();
        for (int index = 0; index < allItems.size(); index++) {
            ItemDTO item = allItems.get(index);
            String itemId = resolveItemId(item);
            try {
                if (!goodsInfoService.isGoodsChanged(item, accountId, syncStatus)) {
                    continue;
                }
                changedItems.add(item);
                if (goodsInfoService.saveOrUpdateGoodsInfo(item, accountId, index + 1, syncStatus) && itemId != null) {
                    respDTO.getUpdatedItemIds().add(itemId);
                }
            } catch (Exception e) {
                log.error("保存商品失败: itemId={}", itemId, e);
            }
        }
        return changedItems;
    }

    private Set<String> collectItemIds(List<ItemDTO> items) {
        Set<String> ids = new HashSet<>();
        if (items == null) {
            return ids;
        }
        for (ItemDTO item : items) {
            String itemId = resolveItemId(item);
            if (itemId != null && !itemId.isBlank()) {
                ids.add(itemId);
            }
        }
        return ids;
    }

    private String resolveItemId(ItemDTO item) {
        if (item == null) {
            return null;
        }
        if (item.getDetailParams() != null && item.getDetailParams().getItemId() != null) {
            return item.getDetailParams().getItemId();
        }
        return item.getId();
    }

    private void fillQuantityFromCardData(ItemDTO itemDTO, Map<String, Object> cardData) {
        Integer quantity = parsePositiveInteger(cardData.get("quantity"));
        if (quantity == null) {
            quantity = parsePositiveInteger(findValue(cardData, "quantity"));
        }
        itemDTO.setQuantity(quantity);
    }

    private void fillPerformanceMetrics(ItemDTO itemDTO, Map<String, Object> cardData) {
        itemDTO.setExposureCount(parseMetricCount(cardData,
                "exposureCount", "exposeCount", "impressionCount", "exposureNum", "exposeNum"));
        itemDTO.setViewCount(parseMetricCount(cardData,
                "viewCount", "browseCount", "pvCount", "visitCount", "clickCount"));
        itemDTO.setWantCount(parseWantCount(cardData, itemDTO));
    }

    private Integer parseWantCount(Map<String, Object> cardData, ItemDTO itemDTO) {
        Integer direct = parseMetricCount(cardData, "wantCount", "wantNum", "wantNumber", "wantPeopleCount");
        if (direct != null) {
            return direct;
        }
        return extractWantCountFromLabelData(itemDTO != null ? itemDTO.getItemLabelDataVO() : null);
    }

    private Integer extractWantCountFromLabelData(ItemDTO.ItemLabelDataVO labelDataVO) {
        if (labelDataVO == null || labelDataVO.getLabelData() == null
                || labelDataVO.getLabelData().getR3() == null
                || labelDataVO.getLabelData().getR3().getTagList() == null) {
            return null;
        }
        for (ItemDTO.Tag tag : labelDataVO.getLabelData().getR3().getTagList()) {
            if (tag == null || tag.getData() == null || tag.getData().getContent() == null) {
                continue;
            }
            Integer count = parseCountFromText(tag.getData().getContent(), "人想要");
            if (count != null) {
                return count;
            }
        }
        return null;
    }

    private Integer parseMetricCount(Map<String, Object> cardData, String... keys) {
        if (cardData == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = findValue(cardData, key);
            Integer parsed = parsePositiveInteger(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Integer parseCountFromText(String text, String suffix) {
        if (text == null || suffix == null || !text.contains(suffix)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        return matcher.find() ? parsePositiveInteger(matcher.group(1)) : null;
    }

    private Object findValue(Object node, String key) {
        if (node instanceof Map<?, ?> map) {
            Object direct = map.get(key);
            if (direct != null) {
                return direct;
            }
            for (Object child : map.values()) {
                Object value = findValue(child, key);
                if (value != null) {
                    return value;
                }
            }
        }
        if (node instanceof List<?> list) {
            for (Object child : list) {
                Object value = findValue(child, key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Integer parsePositiveInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int quantity = Integer.parseInt(String.valueOf(value).trim());
            return quantity > 0 ? quantity : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RefreshItemsRespDTO buildRecoveryRequiredResponse(AllItemsReqDTO reqDTO, CookieRecoveryResult recovery) {
        RefreshItemsRespDTO respDTO = new RefreshItemsRespDTO();
        respDTO.setSuccess(false);
        respDTO.setTotalCount(0);
        respDTO.setSuccessCount(0);
        respDTO.setUpdatedItemIds(new ArrayList<>());
        int syncStatus = reqDTO != null ? normalizeRequestedSyncStatus(reqDTO.getSyncStatus()) : STATUS_ON_SALE;
        respDTO.setSyncStatus(syncStatus);
        respDTO.setSyncLabel(getSyncLabel(syncStatus));
        respDTO.setRecoveryAttempted(recovery != null && recovery.isAttempted());
        respDTO.setNeedCaptcha(recovery != null && recovery.isNeedCaptcha());
        respDTO.setNeedManual(recovery != null && recovery.isNeedManual());
        respDTO.setManualVerifyUrl(recovery != null ? recovery.getManualVerifyUrl() : null);
        respDTO.setCaptchaUrl(recovery != null ? recovery.getCaptchaUrl() : null);
        respDTO.setSessionId(recovery != null ? recovery.getSessionId() : null);
        String message = recovery != null && recovery.getMessage() != null
                ? recovery.getMessage()
                : "已尝试自动刷新和验证，仍失败，请人工更新Cookie或完成滑块验证";
        respDTO.setMessage(message);
        return respDTO;
    }

    static int normalizeItemStatusForSync(Integer syncStatus, Integer apiStatus) {
        int requestedStatus = syncStatus != null ? syncStatus : STATUS_ON_SALE;
        return requestedStatus == STATUS_SOLD ? STATUS_SOLD : STATUS_ON_SALE;
    }

    private int normalizeRequestedSyncStatus(Integer syncStatus) {
        if (syncStatus == null || syncStatus == STATUS_ON_SALE) {
            return STATUS_ON_SALE;
        }
        if (syncStatus == STATUS_SOLD) {
            return STATUS_SOLD;
        }
        throw new IllegalArgumentException("暂不支持同步该商品状态");
    }

    private String getSyncLabel(int syncStatus) {
        return syncStatus == STATUS_SOLD ? GROUP_SOLD : GROUP_ON_SALE;
    }

    private void delayBetweenPages(int syncStatus) {
        log.debug("模拟人工翻页延迟: syncStatus={}", syncStatus);
        if (syncStatus == STATUS_SOLD) {
            com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils.delay(
                    SOLD_PAGE_DELAY_MIN_MS, SOLD_PAGE_DELAY_MAX_MS);
            return;
        }
        com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils.pageScrollDelay();
    }

    @SuppressWarnings("unchecked")
    private SyncGroup resolveSyncGroup(Long accountId, String cookieStr, int syncStatus) {
        String targetGroupName = getSyncLabel(syncStatus);
        SyncGroup fallbackGroup = defaultSyncGroup(syncStatus);
        try {
            Map<String, String> cookies = XianyuSignUtils.parseCookies(cookieStr);
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("needGroupInfo", true);
            dataMap.put("pageNumber", 1);
            dataMap.put("pageSize", 1);
            dataMap.put("groupName", GROUP_ON_SALE);
            dataMap.put("groupId", DEFAULT_ON_SALE_GROUP_ID);
            dataMap.put("defaultGroup", true);
            dataMap.put("userId", cookies.get("unb"));

            XianyuApiRecoveryResult apiResult = xianyuApiRecoveryService.callApi(
                    buildApiRequest(accountId, "商品同步分组解析", ITEM_LIST_API,
                            dataMap, cookieStr, ITEM_LIST_SPM_CNT, ITEM_LIST_SPM_PRE, "1.0"));
            if (!apiResult.isSuccess()) {
                return fallbackGroup;
            }

            Map<String, Object> responseMap = objectMapper.readValue(apiResult.getResponse(), Map.class);
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            if (data == null) {
                return fallbackGroup;
            }

            List<Map<String, Object>> groups = (List<Map<String, Object>>) data.get("itemGroupList");
            if (groups == null) {
                return fallbackGroup;
            }

            for (Map<String, Object> group : groups) {
                if (targetGroupName.equals(String.valueOf(group.get("groupName")))) {
                    String groupId = String.valueOf(group.get("groupId"));
                    boolean defaultGroup = Boolean.TRUE.equals(group.get("defaultGroup"));
                    return new SyncGroup(targetGroupName, groupId, defaultGroup);
                }
            }
        } catch (Exception e) {
            log.warn("解析闲鱼商品分组失败，使用默认分组: syncStatus={}, error={}",
                    syncStatus, e.getMessage());
        }
        return fallbackGroup;
    }

    private SyncGroup defaultSyncGroup(int syncStatus) {
        if (syncStatus == STATUS_SOLD) {
            return new SyncGroup(GROUP_SOLD, "", false);
        }
        return new SyncGroup(GROUP_ON_SALE, DEFAULT_ON_SALE_GROUP_ID, true);
    }

    private boolean shouldRecoverFromError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("令牌已过期")
                || message.contains("令牌过期")
                || message.contains("Token")
                || message.contains("_m_h5_tk")
                || message.contains("Cookie失效")
                || message.contains("Cookie过期")
                || message.contains("RGV587")
                || message.contains("USER_VALIDATE")
                || message.contains("风控")
                || message.contains("滑块");
    }

    private record SyncGroup(String groupName, String groupId, boolean defaultGroup) {
    }
    
    @Override
    public ResultObject<ItemListFromDbRespDTO> getItemsFromDb(ItemListFromDbReqDTO reqDTO) {
        try {
            String keyword = reqDTO.getKeyword() != null ? reqDTO.getKeyword().trim() : null;
            if (keyword != null && keyword.isEmpty()) {
                keyword = null;
            }

            log.info("从数据库获取商品列表: status={}, xianyuAccountId={}, keyword={}, pageNum={}, pageSize={}",
                    reqDTO.getStatus(), reqDTO.getXianyuAccountId(), keyword, reqDTO.getPageNum(), reqDTO.getPageSize());
            
            // 获取分页参数
            int pageSize = reqDTO.getPageSize() != null ? reqDTO.getPageSize() : 20;
            int pageNum = reqDTO.getPageNum() != null ? reqDTO.getPageNum() : 1;
            if (pageSize < 1) {
                pageSize = 20;
            }
            
            // 确保页码有效
            if (pageNum < 1) {
                pageNum = 1;
            }
            
            // 统计总数（使用count查询，提高性能）
            int totalCount = goodsInfoService.countByStatusAndAccountId(
                    reqDTO.getStatus(), reqDTO.getXianyuAccountId(), keyword);
            
            // 计算总页数
            int totalPage = (int) Math.ceil((double) totalCount / pageSize);
            
            // 如果总页数为0，设置为1
            if (totalPage == 0) {
                totalPage = 1;
            }
            
            // 确保页码不超过总页数
            if (pageNum > totalPage && totalPage > 0) {
                pageNum = totalPage;
            }
            
            // 获取当前页的商品列表（带账号ID筛选）
            List<XianyuGoodsInfo> pagedItems = goodsInfoService.listByStatusAndAccountId(
                    reqDTO.getStatus(), reqDTO.getXianyuAccountId(), keyword, pageNum, pageSize);
            
            // 如果分页查询结果为null，创建空列表
            if (pagedItems == null) {
                pagedItems = new ArrayList<>();
            }
            
            // 为每个商品添加配置信息
            List<ItemWithConfigDTO> itemsWithConfig = new ArrayList<>();
            for (XianyuGoodsInfo item : pagedItems) {
                ItemWithConfigDTO itemWithConfig = new ItemWithConfigDTO();
                itemWithConfig.setItem(item);
                
                // 获取商品配置
                if (item.getXianyuAccountId() != null) {
                    com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig config = 
                            autoDeliveryService.getGoodsConfig(item.getXianyuAccountId(), item.getXyGoodId());
                    
                    if (config != null) {
                        itemWithConfig.setXianyuAutoDeliveryOn(config.getXianyuAutoDeliveryOn());
                        itemWithConfig.setXianyuAutoReplyOn(config.getXianyuAutoReplyOn());
                    } else {
                        itemWithConfig.setXianyuAutoDeliveryOn(0);
                        itemWithConfig.setXianyuAutoReplyOn(0);
                    }
                    
                    // 获取自动发货配置
                    com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig deliveryConfig = 
                            autoDeliveryService.getAutoDeliveryConfig(item.getXianyuAccountId(), item.getXyGoodId());
                    
                    if (deliveryConfig != null) {
                        itemWithConfig.setAutoDeliveryType(deliveryConfig.getDeliveryMode());
                        itemWithConfig.setAutoDeliveryContent(deliveryConfig.getAutoDeliveryContent());
                    }
                } else {
                    itemWithConfig.setXianyuAutoDeliveryOn(0);
                    itemWithConfig.setXianyuAutoReplyOn(0);
                }
                
                itemsWithConfig.add(itemWithConfig);
            }
            
            ItemListFromDbRespDTO respDTO = new ItemListFromDbRespDTO();
            respDTO.setItemsWithConfig(itemsWithConfig);
            respDTO.setTotalCount(totalCount);
            respDTO.setPageNum(pageNum);
            respDTO.setPageSize(pageSize);
            respDTO.setTotalPage(totalPage);
            
            // 添加调试日志
            log.info("分页信息: totalCount={}, pageNum={}, pageSize={}, totalPage={}", 
                    totalCount, pageNum, pageSize, totalPage);
            
            log.info("从数据库获取商品列表成功: 总数={}, 当前页={}, 每页={}, 总页数={}", 
                    totalCount, pageNum, pageSize, totalPage);
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("从数据库获取商品列表失败", e);
            return ResultObject.failed("获取商品列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<ItemDetailRespDTO> getItemDetail(ItemDetailReqDTO reqDTO) {
        try {
            log.info("获取商品详情: xyGoodId={}, cookieId={}", reqDTO.getXyGoodId(), reqDTO.getCookieId());
            
            // 1. 从数据库获取商品基本信息
            XianyuGoodsInfo item = goodsInfoService.getByXyGoodId(reqDTO.getXyGoodId());
            
            if (item == null) {
                return ResultObject.failed("商品不存在");
            }
            
            // 2. 判断是否需要获取详情
            boolean needFetchDetail = false;
            
            // 2.1 如果 detail_info 为空，必须获取
            if (item.getDetailInfo() == null || item.getDetailInfo().isEmpty()) {
                log.info("商品详情为空，需要获取: xyGoodId={}", reqDTO.getXyGoodId());
                needFetchDetail = true;
            }
            // 2.2 如果提供了 cookieId，也尝试获取/更新
            else if (reqDTO.getCookieId() != null && !reqDTO.getCookieId().isEmpty()) {
                log.info("提供了cookieId，尝试更新商品详情: xyGoodId={}", reqDTO.getXyGoodId());
                needFetchDetail = true;
            }
            
            // 3. 如果需要获取详情
            if (needFetchDetail) {
                // 3.1 确定使用哪个cookieId
                String cookieIdToUse = reqDTO.getCookieId();
                
                // 3.2 如果没有提供 cookieId，尝试从商品的 xianyu_account_id 获取
                if (cookieIdToUse == null || cookieIdToUse.isEmpty()) {
                    if (item.getXianyuAccountId() != null) {
                        // 使用商品关联的账号ID
                        cookieIdToUse = String.valueOf(item.getXianyuAccountId());
                        log.info("使用商品关联的账号ID获取详情: xyGoodId={}, accountId={}", 
                                reqDTO.getXyGoodId(), item.getXianyuAccountId());
                    } else {
                        log.warn("商品未关联账号且未提供cookieId，无法获取详情: xyGoodId={}", reqDTO.getXyGoodId());
                        ItemDetailRespDTO respDTO = new ItemDetailRespDTO();
                        respDTO.setItemWithConfig(buildItemWithConfig(item));
                        return ResultObject.failed("商品详情为空，且商品未关联账号，请提供cookieId参数以获取详情");
                    }
                }
                
                // 3.2 调用API获取详情
                try {
                    String detailInfo = fetchItemDetailFromApi(reqDTO.getXyGoodId(), cookieIdToUse);
                    
                    if (detailInfo != null && !detailInfo.isEmpty()) {
                        // 更新数据库中的详情信息
                        goodsInfoService.updateDetailInfo(reqDTO.getXyGoodId(), detailInfo);
                        item.setDetailInfo(detailInfo);
                        log.info("商品详情已更新: xyGoodId={}", reqDTO.getXyGoodId());
                    } else {
                        log.warn("未能获取到商品详情: xyGoodId={}", reqDTO.getXyGoodId());
                    }
                } catch (Exception e) {
                    log.error("获取商品详情失败，返回数据库中的信息: xyGoodId={}", reqDTO.getXyGoodId(), e);
                    // 即使获取详情失败，也返回数据库中的基本信息
                }
            }
            
            ItemDetailRespDTO respDTO = new ItemDetailRespDTO();
            respDTO.setItemWithConfig(buildItemWithConfig(item));
            
            log.info("获取商品详情成功: xyGoodId={}", reqDTO.getXyGoodId());
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取商品详情失败: xyGoodId={}", reqDTO.getXyGoodId(), e);
            return ResultObject.failed("获取商品详情失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建包含配置的商品信息
     */
    private ItemWithConfigDTO buildItemWithConfig(XianyuGoodsInfo item) {
        ItemWithConfigDTO itemWithConfig = new ItemWithConfigDTO();
        itemWithConfig.setItem(item);
        
        // 获取商品配置
        if (item.getXianyuAccountId() != null) {
            com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig config = 
                    autoDeliveryService.getGoodsConfig(item.getXianyuAccountId(), item.getXyGoodId());
            
            if (config != null) {
                itemWithConfig.setXianyuAutoDeliveryOn(config.getXianyuAutoDeliveryOn());
                itemWithConfig.setXianyuAutoReplyOn(config.getXianyuAutoReplyOn());
                itemWithConfig.setXianyuAutoReplyContextOn(config.getXianyuAutoReplyContextOn() != null ? config.getXianyuAutoReplyContextOn() : 1);
            } else {
                itemWithConfig.setXianyuAutoDeliveryOn(0);
                itemWithConfig.setXianyuAutoReplyOn(0);
                itemWithConfig.setXianyuAutoReplyContextOn(1);
            }
            
            // 获取自动发货配置
            com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig deliveryConfig = 
                    autoDeliveryService.getAutoDeliveryConfig(item.getXianyuAccountId(), item.getXyGoodId());
            
            if (deliveryConfig != null) {
                itemWithConfig.setAutoDeliveryType(deliveryConfig.getDeliveryMode());
                itemWithConfig.setAutoDeliveryContent(deliveryConfig.getAutoDeliveryContent());
            }
        } else {
            itemWithConfig.setXianyuAutoDeliveryOn(0);
            itemWithConfig.setXianyuAutoReplyOn(0);
            itemWithConfig.setXianyuAutoReplyContextOn(1);
        }
        
        return itemWithConfig;
    }
    
    /**
     * 从闲鱼API获取商品详情
     * 实现流程：
     * 1. 检查缓存（24小时内的详情不重复获取）
     * 2. 首选：通过闲鱼API mtop.taobao.idle.pc.detail 获取
     * 3. 备选：如果API失败，可以考虑使用浏览器访问（需要额外实现）
     *
     * @param itemId 商品ID
     * @param cookieId Cookie ID
     * @return 商品详情JSON字符串
     */
    private String fetchItemDetailFromApi(String itemId, String cookieId) {
        try {
            log.info("开始获取商品详情: itemId={}, cookieId={}", itemId, cookieId);
            
            // 1. 检查缓存：如果数据库中已有详情且在24小时内，直接返回
            XianyuGoodsInfo cachedItem = goodsInfoService.getByXyGoodId(itemId);
            if (cachedItem != null && cachedItem.getDetailInfo() != null && !cachedItem.getDetailInfo().isEmpty()) {
                // 检查更新时间是否在24小时内
                if (isDetailInfoFresh(cachedItem.getUpdatedTime())) {
                    log.info("使用缓存的商品详情: itemId={}, 缓存时间={}", itemId, cachedItem.getUpdatedTime());
                    return cachedItem.getDetailInfo();
                } else {
                    log.info("缓存的商品详情已过期，重新获取: itemId={}", itemId);
                }
            } else {
                log.info("数据库中没有商品详情缓存，需要调用API获取: itemId={}", itemId);
            }
            
            // 2. 从数据库获取Cookie
            String cookiesStr = getCookieFromDb(cookieId);
            if (cookiesStr == null || cookiesStr.isEmpty()) {
                log.error("未找到账号Cookie: cookieId={}", cookieId);
                return null;
            }
            
            log.info("Cookie获取成功，准备调用API: itemId={}", itemId);
            
            // 3. 首选方式：通过闲鱼API获取商品详情
            Long accountId = getAccountIdFromCookieId(cookieId);
            if (accountId == null) {
                log.warn("无法根据cookieId解析账号ID，跳过自动恢复: cookieId={}", cookieId);
            }
            String detailJson = fetchDetailFromApi(accountId, itemId, cookiesStr);
            
            if (detailJson != null && !detailJson.isEmpty()) {
                log.info("通过API获取商品详情成功: itemId={}, 详情长度={}", itemId, detailJson.length());
                return detailJson;
            }
            
            // 4. 备选方式：通过浏览器访问获取（暂未实现）
            log.warn("API获取商品详情失败，备选方式（浏览器访问）暂未实现: itemId={}", itemId);
            
            // 如果有缓存的详情（即使过期），也返回它
            if (cachedItem != null && cachedItem.getDetailInfo() != null && !cachedItem.getDetailInfo().isEmpty()) {
                log.info("返回过期的缓存详情: itemId={}", itemId);
                return cachedItem.getDetailInfo();
            }
            
            log.error("无法获取商品详情，且没有可用的缓存: itemId={}", itemId);
            return null;
            
        } catch (Exception e) {
            log.error("获取商品详情异常: itemId={}", itemId, e);
            return null;
        }
    }
    
    /**
     * 通过闲鱼API获取商品详情
     *
     * @param itemId 商品ID
     * @param cookiesStr Cookie字符串
     * @return 商品详情JSON字符串
     */
    private String fetchDetailFromApi(Long accountId, String itemId, String cookiesStr) {
        try {
            log.info("调用闲鱼API获取商品详情: itemId={}", itemId);
            
            // 构建请求数据
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("itemId", itemId);
            
            XianyuApiRecoveryResult apiResult = xianyuApiRecoveryService.callApi(
                    buildApiRequest(accountId, "商品详情获取", "mtop.taobao.idle.pc.detail",
                            dataMap, cookiesStr, null, null, "1.0"));

            if (!apiResult.isSuccess()) {
                log.error("API调用失败: itemId={}, error={}", itemId, apiResult.getErrorMessage());
                return null;
            }
            String response = apiResult.getResponse();
            
            log.info("API响应成功，响应长度: {}, itemId={}", response.length(), itemId);
            
            // 检查响应是否成功
            if (!XianyuApiUtils.isSuccess(response)) {
                String error = XianyuApiUtils.extractError(response);
                log.error("API返回失败: {}, itemId={}", error, itemId);
                // 打印完整响应用于调试
                log.error("完整响应内容: {}", response);
                return null;
            }
            
            log.info("API响应状态检查通过，开始提取data字段: itemId={}", itemId);
            
            // 提取data字段
            Map<String, Object> data = XianyuApiUtils.extractData(response);
            if (data == null) {
                log.error("无法提取data字段, itemId={}", itemId);
                log.error("响应内容: {}", response);
                return null;
            }
            
            log.info("data字段提取成功，包含 {} 个字段, itemId={}", data.size(), itemId);
            
            // 将data转换为JSON字符串
            String detailJson = objectMapper.writeValueAsString(data);
            log.info("API获取商品详情成功: itemId={}, 详情长度={}", itemId, detailJson.length());
            
            // 提取desc字段
            String extractedDesc = ItemDetailUtils.extractDescFromDetailJson(detailJson);
            log.info("提取desc字段成功: itemId={}, 原始长度={}, 提取后长度={}", 
                    itemId, detailJson.length(), extractedDesc.length());
            
            return extractedDesc;
            
        } catch (Exception e) {
            log.error("API获取商品详情异常: itemId={}", itemId, e);
            return null;
        }
    }

    private XianyuApiRecoveryRequest buildApiRequest(Long accountId, String operationName, String apiName,
                                                     Map<String, Object> dataMap, String cookie,
                                                     String spmCnt, String spmPre, String version) {
        XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
        request.setAccountId(accountId);
        request.setOperationName(operationName);
        request.setApiName(apiName);
        request.setDataMap(dataMap);
        request.setCookie(cookie);
        request.setSpmCnt(spmCnt);
        request.setSpmPre(spmPre);
        request.setVersion(version);
        return request;
    }
    
    /**
     * 检查详情信息是否新鲜（24小时内）
     *
     * @param updatedTime 更新时间字符串（格式：yyyy-MM-dd HH:mm:ss）
     * @return 是否新鲜
     */
    private boolean isDetailInfoFresh(String updatedTime) {
        if (updatedTime == null || updatedTime.isEmpty()) {
            return false;
        }
        
        try {
            // 解析更新时间
            java.time.LocalDateTime updateDateTime = java.time.LocalDateTime.parse(
                updatedTime, 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            );
            
            // 计算时间差
            java.time.Duration duration = java.time.Duration.between(updateDateTime, java.time.LocalDateTime.now());
            long hours = duration.toHours();
            
            // 24小时内认为是新鲜的
            boolean isFresh = hours < 24;
            log.debug("详情缓存检查: 更新时间={}, 距今{}小时, 是否新鲜={}", updatedTime, hours, isFresh);
            
            return isFresh;
            
        } catch (Exception e) {
            log.error("解析更新时间失败: {}", updatedTime, e);
            return false;
        }
    }

    /**
     * 解析商品列表响应
     */
    @SuppressWarnings("unchecked")
    private ItemListRespDTO parseItemListResponse(Map<String, Object> responseMap, int pageNumber, int pageSize) {
        ItemListRespDTO respDTO = new ItemListRespDTO();
        respDTO.setPageNumber(pageNumber);
        respDTO.setPageSize(pageSize);
        respDTO.setItems(new ArrayList<>());

        try {
            log.info("开始解析响应，responseMap keys: {}", responseMap.keySet());
            
            List<String> ret = (List<String>) responseMap.get("ret");
            log.info("ret字段: {}", ret);
            
            // 检查令牌是否过期
            if (ret != null && !ret.isEmpty()) {
                String retValue = ret.get(0);
                if (isRecoverableRet(retValue)) {
                    log.warn("API调用失败，ret: {}", ret);
                    throw new com.feijimiao.xianyuassistant.exception.BusinessException(buildRecoverableRetMessage(retValue));
                }
            }
            
            if (ret != null && !ret.isEmpty() && ret.get(0).contains("SUCCESS")) {
                log.info("API调用成功，开始解析数据");
                respDTO.setSuccess(true);

                Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
                log.info("data字段存在: {}, keys: {}", data != null, data != null ? data.keySet() : "null");
                
                if (data != null) {
                    List<Map<String, Object>> cardList = (List<Map<String, Object>>) data.get("cardList");
                    log.info("cardList存在: {}, size: {}", cardList != null, cardList != null ? cardList.size() : 0);
                    
                    if (cardList != null) {
                        for (Map<String, Object> card : cardList) {
                            Map<String, Object> cardData = (Map<String, Object>) card.get("cardData");
                            if (cardData != null) {
                                log.info("商品cardData: {}", cardData);
                                ItemDTO itemDTO = objectMapper.convertValue(cardData, ItemDTO.class);
                                fillQuantityFromCardData(itemDTO, cardData);
                                fillPerformanceMetrics(itemDTO, cardData);
                                respDTO.getItems().add(itemDTO);
                            }
                        }
                    }
                }

                respDTO.setCurrentCount(respDTO.getItems().size());
                respDTO.setSavedCount(respDTO.getItems().size());
                log.info("解析完成，商品数量: {}", respDTO.getItems().size());
            } else {
                log.warn("API调用失败，ret: {}", ret);
                respDTO.setSuccess(false);
            }
        } catch (com.feijimiao.xianyuassistant.exception.BusinessException e) {
            // 重新抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("解析商品列表响应失败", e);
            respDTO.setSuccess(false);
        }

        return respDTO;
    }

    private boolean isRecoverableRet(String retValue) {
        if (retValue == null) {
            return false;
        }
        return retValue.contains("FAIL_SYS_TOKEN_EXOIRED")
                || retValue.contains("FAIL_SYS_TOKEN_EXPIRED")
                || retValue.contains("FAIL_SYS_SESSION_EXPIRED")
                || retValue.contains("SID_INVALID")
                || retValue.contains("令牌过期")
                || retValue.contains("RGV587")
                || retValue.contains("FAIL_SYS_RGV587_ERROR")
                || retValue.contains("FAIL_SYS_USER_VALIDATE")
                || retValue.contains("被挤爆");
    }

    private String buildRecoverableRetMessage(String retValue) {
        if (retValue != null && (retValue.contains("RGV587")
                || retValue.contains("USER_VALIDATE")
                || retValue.contains("被挤爆"))) {
            return "Cookie失效/风控，需要自动恢复: " + retValue;
        }
        return "令牌已过期，需要自动恢复: " + retValue;
    }

    /**
     * 从cookieId获取账号ID
     * cookieId可以是：账号ID、账号备注(account_note)或UNB
     *
     * @param cookieId Cookie ID
     * @return 账号ID
     */
    private Long getAccountIdFromCookieId(String cookieId) {
        try {
            // 1. 先尝试作为账号ID解析（数字）
            try {
                return Long.parseLong(cookieId);
            } catch (NumberFormatException e) {
                // 不是数字，继续其他方式查询
                log.debug("cookieId不是数字，尝试其他查询方式: {}", cookieId);
            }
            
            // 2. 尝试按账号备注查询
            Long accountId = accountService.getAccountIdByAccountNote(cookieId);
            if (accountId != null) {
                log.info("通过账号备注获取账号ID成功: accountNote={}, accountId={}", cookieId, accountId);
                return accountId;
            }
            
            // 3. 尝试按UNB查询
            accountId = accountService.getAccountIdByUnb(cookieId);
            if (accountId != null) {
                log.info("通过UNB获取账号ID成功: unb={}, accountId={}", cookieId, accountId);
                return accountId;
            }
            
            log.warn("未找到账号ID: cookieId={}", cookieId);
            return null;
            
        } catch (Exception e) {
            log.error("获取账号ID失败: cookieId={}", cookieId, e);
            return null;
        }
    }
    
    /**
     * 从数据库获取Cookie（包含 m_h5_tk 补充逻辑）
     * cookieId可以是：账号ID、账号备注(account_note)或UNB
     */
    private String getCookieFromDb(String cookieId) {
        try {
            log.info("从数据库查询Cookie: cookieId={}", cookieId);
            
            String cookie = null;
            
            // 1. 先尝试作为账号ID查询（数字）
            try {
                Long accountId = Long.parseLong(cookieId);
                cookie = accountService.getCookieByAccountId(accountId);
                if (cookie != null) {
                    log.info("通过账号ID获取Cookie成功: accountId={}", accountId);
                    // 检查并补充 _m_h5_tk
                    cookie = ensureMh5tkInCookie(cookie, accountId);
                    return cookie;
                }
            } catch (NumberFormatException e) {
                // 不是数字，继续其他方式查询
                log.debug("cookieId不是数字，尝试其他查询方式: {}", cookieId);
            }
            
            // 2. 尝试按账号备注查询
            cookie = accountService.getCookieByAccountNote(cookieId);
            if (cookie != null) {
                log.info("通过账号备注获取Cookie成功: accountNote={}", cookieId);
                return cookie;
            }
            
            // 3. 尝试按UNB查询
            cookie = accountService.getCookieByUnb(cookieId);
            if (cookie != null) {
                log.info("通过UNB获取Cookie成功: unb={}", cookieId);
                return cookie;
            }
            
            log.warn("未找到Cookie: cookieId={}", cookieId);
            return null;
            
        } catch (Exception e) {
            log.error("从数据库获取Cookie失败: cookieId={}", cookieId, e);
            return null;
        }
    }
    
    @Override
    public ResultObject<UpdateAutoDeliveryRespDTO> updateAutoDeliveryStatus(UpdateAutoDeliveryReqDTO reqDTO) {
        try {
            log.info("更新商品自动发货状态: xianyuAccountId={}, xyGoodsId={}, status={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getXianyuAutoDeliveryOn());
            
            // 1. 获取商品配置
            com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig goodsConfig = 
                    autoDeliveryService.getGoodsConfig(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
            
            // 2. 如果配置不存在，创建新的配置
            if (goodsConfig == null) {
                goodsConfig = new com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig();
                goodsConfig.setXianyuAccountId(reqDTO.getXianyuAccountId());
                goodsConfig.setXyGoodsId(reqDTO.getXyGoodsId());
                goodsConfig.setXianyuAutoDeliveryOn(reqDTO.getXianyuAutoDeliveryOn());
                goodsConfig.setXianyuAutoReplyOn(0); // 默认关闭自动回复
                goodsConfig.setCreateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                goodsConfig.setUpdateTime(goodsConfig.getCreateTime());
            } else {
                // 3. 更新配置
                goodsConfig.setXianyuAutoDeliveryOn(reqDTO.getXianyuAutoDeliveryOn());
                goodsConfig.setUpdateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            }
            
            // 4. 保存配置
            autoDeliveryService.saveOrUpdateGoodsConfig(goodsConfig);
            
            // 5. 返回结果
            UpdateAutoDeliveryRespDTO respDTO = new UpdateAutoDeliveryRespDTO();
            respDTO.setSuccess(true);
            respDTO.setMessage("自动发货状态更新成功");
            
            log.info("自动发货状态更新成功: xianyuAccountId={}, xyGoodsId={}, status={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getXianyuAutoDeliveryOn());
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("更新自动发货状态失败: xianyuAccountId={}, xyGoodsId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("更新自动发货状态失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<UpdateAutoReplyRespDTO> updateAutoReplyStatus(UpdateAutoReplyReqDTO reqDTO) {
        try {
            log.info("更新商品自动回复状态: xianyuAccountId={}, xyGoodsId={}, status={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getXianyuAutoReplyOn());
            
            // 1. 获取商品配置
            com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig goodsConfig = 
                    autoDeliveryService.getGoodsConfig(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
            
            // 2. 如果配置不存在，创建新的配置
            if (goodsConfig == null) {
                goodsConfig = new com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig();
                goodsConfig.setXianyuAccountId(reqDTO.getXianyuAccountId());
                goodsConfig.setXyGoodsId(reqDTO.getXyGoodsId());
                goodsConfig.setXianyuAutoDeliveryOn(0); // 默认关闭自动发货
                goodsConfig.setXianyuAutoReplyOn(reqDTO.getXianyuAutoReplyOn());
                // 携带上下文开关：第一次跟随自动回复开关默认开启
                if (reqDTO.getXianyuAutoReplyContextOn() != null) {
                    goodsConfig.setXianyuAutoReplyContextOn(reqDTO.getXianyuAutoReplyContextOn());
                } else {
                    goodsConfig.setXianyuAutoReplyContextOn(1); // 默认开启
                }
                goodsConfig.setCreateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                goodsConfig.setUpdateTime(goodsConfig.getCreateTime());
            } else {
                // 3. 更新配置
                goodsConfig.setXianyuAutoReplyOn(reqDTO.getXianyuAutoReplyOn());
                // 更新携带上下文开关
                if (reqDTO.getXianyuAutoReplyContextOn() != null) {
                    goodsConfig.setXianyuAutoReplyContextOn(reqDTO.getXianyuAutoReplyContextOn());
                }
                goodsConfig.setUpdateTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            }
            
            // 4. 保存配置
            autoDeliveryService.saveOrUpdateGoodsConfig(goodsConfig);
            
            // 5. 返回结果
            UpdateAutoReplyRespDTO respDTO = new UpdateAutoReplyRespDTO();
            respDTO.setSuccess(true);
            respDTO.setMessage("自动回复状态更新成功");
            
            log.info("自动回复状态更新成功: xianyuAccountId={}, xyGoodsId={}, status={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getXianyuAutoReplyOn());
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("更新自动回复状态失败: xianyuAccountId={}, xyGoodsId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("更新自动回复状态失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<DeleteItemRespDTO> deleteItem(DeleteItemReqDTO reqDTO) {
        try {
            log.info("开始删除本地商品: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());

            String validationError = validateItemActionReq(reqDTO);
            if (validationError != null) {
                return ResultObject.failed(validationError);
            }

            XianyuGoodsInfo goodsInfo = loadOwnedGoods(reqDTO);
            if (goodsInfo == null) {
                return ResultObject.failed("商品不存在");
            }
            boolean deleted = goodsInfoService.deleteGoodsInfo(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
            
            DeleteItemRespDTO respDTO = new DeleteItemRespDTO();
            if (deleted) {
                respDTO.setMessage("本地商品删除成功");
                log.info("本地商品删除成功: xianyuAccountId={}, xyGoodsId={}",
                        reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
                return ResultObject.success(respDTO);
            } else {
                respDTO.setMessage("商品删除失败，商品可能不存在");
                log.warn("商品删除失败: xianyuAccountId={}, xyGoodsId={}", 
                        reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
                return ResultObject.failed("商品删除失败");
            }
        } catch (Exception e) {
            log.error("删除商品异常: xianyuAccountId={}, xyGoodsId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("删除商品异常: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> remoteDeleteItem(DeleteItemReqDTO reqDTO) {
        try {
            log.info("开始远程删除闲鱼商品: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());

            if (!isFeatureEnabled(GOODS_DELETE_ENABLED_KEY, true)) {
                return ResultObject.failed("闲鱼删除功能已关闭");
            }

            String validationError = validateItemActionReq(reqDTO);
            if (validationError != null) {
                return ResultObject.failed(validationError);
            }

            XianyuGoodsInfo goodsInfo = loadOwnedGoods(reqDTO);
            if (goodsInfo == null) {
                return ResultObject.failed("商品不存在");
            }
            if (Objects.equals(goodsInfo.getStatus(), STATUS_REMOTE_DELETED)) {
                return ResultObject.success("闲鱼商品已删除");
            }

            XianyuAccount account = loadAccount(reqDTO.getXianyuAccountId());
            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }

            itemOperateService.deleteItem(account, cookie, goodsInfo);
            goodsInfoService.updateGoodsStatus(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), STATUS_REMOTE_DELETED);
            return ResultObject.success("闲鱼商品删除成功，本地商品已保留");
        } catch (Exception e) {
            log.error("远程删除闲鱼商品异常: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("远程删除闲鱼商品异常: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> offShelfItem(DeleteItemReqDTO reqDTO) {
        try {
            log.info("开始下架商品: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());

            if (!isFeatureEnabled(GOODS_OFF_SHELF_ENABLED_KEY, true)) {
                return ResultObject.failed("商品下架功能已关闭");
            }

            String validationError = validateItemActionReq(reqDTO);
            if (validationError != null) {
                return ResultObject.failed(validationError);
            }

            XianyuGoodsInfo goodsInfo = loadOwnedGoods(reqDTO);
            if (goodsInfo == null) {
                return ResultObject.failed("商品不存在");
            }
            if (Objects.equals(goodsInfo.getStatus(), STATUS_OFF_SHELF)) {
                return ResultObject.success("商品已下架");
            }
            XianyuAccount account = loadAccount(reqDTO.getXianyuAccountId());

            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }

            itemOperateService.offShelfItem(account, cookie, goodsInfo);

            boolean updated = goodsInfoService.updateGoodsStatus(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), STATUS_OFF_SHELF);
            if (!updated) {
                return ResultObject.failed("下架成功，但本地状态更新失败");
            }

            return ResultObject.success("商品下架成功");
        } catch (Exception e) {
            log.error("下架商品异常: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("下架商品异常: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> updateItemPrice(UpdateItemPriceReqDTO reqDTO) {
        try {
            log.info("开始修改商品价格: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());

            String validationError = validateUpdatePriceReq(reqDTO);
            if (validationError != null) {
                return ResultObject.failed(validationError);
            }

            XianyuGoodsInfo goodsInfo = loadOwnedGoods(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
            if (goodsInfo == null) {
                return ResultObject.failed("商品不存在");
            }
            XianyuAccount account = loadAccount(reqDTO.getXianyuAccountId());
            if (!Boolean.TRUE.equals(account.getFishShopUser())) {
                return ResultObject.failed("当前账号不是鱼小铺，无法改价");
            }

            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }

            String price = normalizePrice(reqDTO.getPrice());
            itemOperateService.updatePrice(account, cookie, goodsInfo, price);
            boolean updated = goodsInfoService.updateGoodsPrice(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), price);
            if (!updated) {
                return ResultObject.failed("改价成功，但本地价格更新失败");
            }
            return ResultObject.success("商品改价成功");
        } catch (Exception e) {
            log.error("修改商品价格异常: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("修改商品价格异常: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> updateItemStock(UpdateItemStockReqDTO reqDTO) {
        try {
            log.info("开始修改商品库存: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());

            String validationError = validateUpdateStockReq(reqDTO);
            if (validationError != null) {
                return ResultObject.failed(validationError);
            }

            XianyuGoodsInfo goodsInfo = loadOwnedGoods(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
            if (goodsInfo == null) {
                return ResultObject.failed("商品不存在");
            }
            XianyuAccount account = loadAccount(reqDTO.getXianyuAccountId());
            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }

            itemOperateService.updateQuantity(account, cookie, goodsInfo, reqDTO.getQuantity());
            boolean updated = goodsInfoService.updateGoodsQuantity(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getQuantity());
            if (!updated) {
                return ResultObject.failed("改库存成功，但本地库存更新失败");
            }
            return ResultObject.success("商品库存修改成功");
        } catch (Exception e) {
            log.error("修改商品库存异常: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("修改商品库存异常: " + e.getMessage());
        }
    }

    @Override
    public ResultObject<String> republishItem(DeleteItemReqDTO reqDTO) {
        try {
            log.info("开始恢复原商品在售: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());

            if (!isFeatureEnabled(GOODS_OFF_SHELF_ENABLED_KEY, true)) {
                return ResultObject.failed("商品上架功能已关闭");
            }

            String validationError = validateItemActionReq(reqDTO);
            if (validationError != null) {
                return ResultObject.failed(validationError);
            }

            XianyuGoodsInfo goodsInfo = loadOwnedGoods(reqDTO);
            if (goodsInfo == null) {
                return ResultObject.failed("商品不存在");
            }
            if (Objects.equals(goodsInfo.getStatus(), STATUS_ON_SALE)) {
                return ResultObject.success("商品已在售");
            }

            XianyuAccount account = loadAccount(reqDTO.getXianyuAccountId());
            String cookie = accountService.getCookieByAccountId(reqDTO.getXianyuAccountId());
            if (cookie == null || cookie.isBlank()) {
                return ResultObject.failed("未找到账号Cookie，请先登录");
            }

            itemOperateService.upShelfItem(account, cookie, goodsInfo);
            boolean updated = goodsInfoService.updateGoodsStatus(
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), STATUS_ON_SALE);
            if (!updated) {
                return ResultObject.failed("商品恢复上架成功，但本地状态更新失败");
            }
            return ResultObject.success("商品已恢复在售");
        } catch (Exception e) {
            log.error("恢复上架商品异常: xianyuAccountId={}, xyGoodsId={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), e);
            return ResultObject.failed("恢复上架商品异常: " + e.getMessage());
        }
    }

    private String validateItemActionReq(DeleteItemReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return "账号ID不能为空";
        }
        if (reqDTO.getXyGoodsId() == null || reqDTO.getXyGoodsId().isBlank()) {
            return "商品ID不能为空";
        }
        return null;
    }

    private String validateUpdatePriceReq(UpdateItemPriceReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return "账号ID不能为空";
        }
        if (reqDTO.getXyGoodsId() == null || reqDTO.getXyGoodsId().isBlank()) {
            return "商品ID不能为空";
        }
        try {
            normalizePrice(reqDTO.getPrice());
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private String validateUpdateStockReq(UpdateItemStockReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return "账号ID不能为空";
        }
        if (reqDTO.getXyGoodsId() == null || reqDTO.getXyGoodsId().isBlank()) {
            return "商品ID不能为空";
        }
        if (reqDTO.getQuantity() == null || reqDTO.getQuantity() <= 0) {
            return "库存必须大于0";
        }
        return null;
    }

    private XianyuGoodsInfo loadOwnedGoods(DeleteItemReqDTO reqDTO) {
        return loadOwnedGoods(reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId());
    }

    private XianyuGoodsInfo loadOwnedGoods(Long accountId, String xyGoodsId) {
        XianyuGoodsInfo goodsInfo = goodsInfoService.getByXyGoodIdAndAccountId(accountId, xyGoodsId);
        if (goodsInfo == null) {
            return null;
        }
        return goodsInfo;
    }

    private XianyuAccount loadAccount(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalStateException("账号不存在");
        }
        return account;
    }

    private boolean isFeatureEnabled(String key, boolean defaultValue) {
        String value = sysSettingService.getSettingValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private String normalizePrice(String price) {
        if (price == null || price.trim().isEmpty()) {
            throw new IllegalArgumentException("价格不能为空");
        }
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(price.trim());
            if (value.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("价格必须大于0");
            }
            if (value.scale() > 2) {
                throw new IllegalArgumentException("价格最多保留2位小数");
            }
            return value.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("价格最多保留2位小数");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("价格格式不正确");
        }
    }
    
    /**
     * 确保Cookie中包含 _m_h5_tk
     * 如果cookie_text中没有，则从数据库的m_h5_tk字段补充
     */
    private String ensureMh5tkInCookie(String cookieText, Long accountId) {
        try {
            // 解析Cookie
            Map<String, String> cookies = XianyuSignUtils.parseCookies(cookieText);
            
            // 如果已经包含 _m_h5_tk，直接返回
            if (cookies.containsKey("_m_h5_tk") && !cookies.get("_m_h5_tk").isEmpty()) {
                return cookieText;
            }
            
            // 从数据库获取 m_h5_tk
            String mH5Tk = accountService.getMh5tkByAccountId(accountId);
            if (mH5Tk != null && !mH5Tk.isEmpty()) {
                log.info("从数据库m_h5_tk字段补充token: accountId={}", accountId);
                cookies.put("_m_h5_tk", mH5Tk);
                return XianyuSignUtils.formatCookies(cookies);
            }
            
            log.warn("数据库中也没有m_h5_tk: accountId={}", accountId);
            return cookieText;
            
        } catch (Exception e) {
            log.error("补充m_h5_tk失败: accountId={}", accountId, e);
            return cookieText;
        }
    }
    

    
    /**
     * 获取自动回复配置
     */
    @Override
    public ResultObject<RagAutoReplyConfigRespDTO> getRagAutoReplyConfig(RagAutoReplyConfigReqDTO reqDTO) {
        try {
            Long accountId = reqDTO.getXianyuAccountId();
            String xyGoodsId = reqDTO.getXyGoodsId();

            RagAutoReplyConfigRespDTO respDTO = new RagAutoReplyConfigRespDTO();
            respDTO.setRagDelaySeconds(15);
            if (hasText(xyGoodsId)) {
                com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig config =
                        autoDeliveryConfigMapper.findByAccountIdAndGoodsId(accountId, xyGoodsId);
                if (config != null && config.getRagDelaySeconds() != null) {
                    respDTO.setRagDelaySeconds(config.getRagDelaySeconds());
                }
            }
            respDTO.setGlobalAiReplyTemplate(getGlobalAiReplyTemplate(accountId));
            respDTO.setGlobalAiReplyEnabled(isGlobalAiReplyEnabled(accountId));
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取自动回复配置失败", e);
            return ResultObject.failed("获取自动回复配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新自动回复配置
     */
    @Override
    public ResultObject<?> updateRagAutoReplyConfig(UpdateRagAutoReplyConfigReqDTO reqDTO) {
        try {
            Long accountId = reqDTO.getXianyuAccountId();
            String xyGoodsId = reqDTO.getXyGoodsId();
            Integer ragDelaySeconds = reqDTO.getRagDelaySeconds();
            String globalTemplate = reqDTO.getGlobalAiReplyTemplate();
            Boolean globalEnabled = reqDTO.getGlobalAiReplyEnabled();

            if (hasText(xyGoodsId)) {
                com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig config =
                        autoDeliveryConfigMapper.findByAccountIdAndGoodsId(accountId, xyGoodsId);

                if (config == null) {
                    config = new com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig();
                    config.setXianyuAccountId(accountId);
                    config.setXyGoodsId(xyGoodsId);
                    config.setRagDelaySeconds(ragDelaySeconds != null ? ragDelaySeconds : 15);
                    autoDeliveryConfigMapper.insert(config);
                } else {
                    config.setRagDelaySeconds(ragDelaySeconds != null ? ragDelaySeconds : 15);
                    autoDeliveryConfigMapper.updateById(config);
                }
            }
            if (globalTemplate != null) {
                saveGlobalAiReplyTemplate(accountId, globalTemplate);
            }
            if (globalEnabled != null) {
                saveGlobalAiReplyEnabled(accountId, globalEnabled);
            }

            log.info("更新自动回复延时配置成功: accountId={}, xyGoodsId={}, ragDelaySeconds={}", 
                    accountId, xyGoodsId, ragDelaySeconds);
            RagAutoReplyConfigReqDTO readBackReq = new RagAutoReplyConfigReqDTO();
            readBackReq.setXianyuAccountId(accountId);
            readBackReq.setXyGoodsId(xyGoodsId);
            return getRagAutoReplyConfig(readBackReq);
        } catch (Exception e) {
            log.error("更新自动回复配置失败", e);
            return ResultObject.failed("更新自动回复配置失败: " + e.getMessage());
        }
    }

    private String getGlobalAiReplyTemplate(Long accountId) {
        if (accountId == null) {
            return "";
        }
        String value = sysSettingService.getSettingValue(globalTemplateKey(accountId));
        return value == null ? "" : value;
    }

    private void saveGlobalAiReplyTemplate(Long accountId, String template) {
        if (accountId == null) {
            return;
        }
        SaveSettingReqBO reqBO = new SaveSettingReqBO();
        reqBO.setSettingKey(globalTemplateKey(accountId));
        reqBO.setSettingValue(template == null ? "" : template.trim());
        reqBO.setSettingDesc("账号级全局AI回复模板");
        sysSettingService.saveSetting(reqBO);
    }

    private Boolean isGlobalAiReplyEnabled(Long accountId) {
        if (accountId == null) {
            return false;
        }
        return "1".equals(sysSettingService.getSettingValue(globalEnabledKey(accountId)));
    }

    private void saveGlobalAiReplyEnabled(Long accountId, boolean enabled) {
        if (accountId == null) {
            return;
        }
        SaveSettingReqBO reqBO = new SaveSettingReqBO();
        reqBO.setSettingKey(globalEnabledKey(accountId));
        reqBO.setSettingValue(enabled ? "1" : "0");
        reqBO.setSettingDesc("账号级所有商品AI回复总开关");
        sysSettingService.saveSetting(reqBO);
    }

    private String globalTemplateKey(Long accountId) {
        return GLOBAL_AI_REPLY_TEMPLATE_PREFIX + accountId;
    }

    private String globalEnabledKey(Long accountId) {
        return "global_ai_reply_enabled_" + accountId;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
