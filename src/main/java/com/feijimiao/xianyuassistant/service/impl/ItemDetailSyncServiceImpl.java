package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.controller.dto.ItemDTO;
import com.feijimiao.xianyuassistant.controller.dto.SyncProgressRespDTO;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.service.ItemDetailSyncService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import com.feijimiao.xianyuassistant.utils.ItemDetailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ItemDetailSyncServiceImpl implements ItemDetailSyncService {

    private static final int STATUS_SOLD = 2;
    private static final int ON_SALE_BASE_DELAY_MS = 1500;
    private static final int ON_SALE_JITTER_MS = 2500;
    private static final int SOLD_BASE_DELAY_MS = 5000;
    private static final int SOLD_JITTER_MS = 5000;

    @Autowired
    private AccountService accountService;

    @Autowired
    private GoodsInfoService goodsInfoService;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    private final ConcurrentHashMap<String, SyncProgress> progressMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> accountSyncMap = new ConcurrentHashMap<>();

    private static class SyncProgress {
        String syncId;
        Long accountId;
        int totalCount;
        volatile int completedCount = 0;
        volatile int successCount = 0;
        volatile int failedCount = 0;
        volatile boolean isCompleted = false;
        volatile boolean isRunning = true;
        volatile String currentItemId = null;
        volatile String message = "同步中...";
        long startTime;
        volatile boolean cancelled = false;
    }

    @Override
    public String startSync(Long accountId, List<ItemDTO> items) {
        if (isSyncing(accountId)) {
            String existingSyncId = accountSyncMap.get(accountId);
            log.info("账号已有同步任务进行中: accountId={}, syncId={}", accountId, existingSyncId);
            return existingSyncId;
        }

        String syncId = UUID.randomUUID().toString();
        SyncProgress progress = new SyncProgress();
        progress.syncId = syncId;
        progress.accountId = accountId;
        progress.totalCount = items.size();
        progress.startTime = System.currentTimeMillis();

        progressMap.put(syncId, progress);
        accountSyncMap.put(accountId, syncId);

        log.info("启动异步详情同步: syncId={}, accountId={}, itemCount={}", syncId, accountId, items.size());
        String cookieStr = accountService.getCookieByAccountId(accountId);
        List<ItemDTO> syncItems = new ArrayList<>(items);
        taskExecutor.execute(() -> executeSync(syncId, accountId, syncItems, cookieStr));
        return syncId;
    }

    private void executeSync(String syncId, Long accountId, List<ItemDTO> items, String cookieStr) {
        SyncProgress progress = progressMap.get(syncId);
        if (progress == null) {
            log.error("同步进度不存在: syncId={}", syncId);
            return;
        }

        try {
            if (cookieStr == null || cookieStr.isBlank()) {
                progress.failedCount = progress.totalCount;
                progress.isCompleted = true;
                progress.isRunning = false;
                progress.message = "同步失败: 未找到账号Cookie";
                return;
            }

            for (ItemDTO item : items) {
                if (progress.cancelled) {
                    progress.message = "同步已取消";
                    break;
                }

                String itemId = resolveItemId(item);
                if (itemId == null || itemId.isEmpty()) {
                    progress.completedCount++;
                    progress.failedCount++;
                    continue;
                }

                progress.currentItemId = itemId;

                try {
                    Thread.sleep(resolveDelayMillis(item));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                DetailFetchResult detailResult = fetchAndSaveDetail(accountId, itemId, cookieStr);
                if (detailResult.cookieText() != null && !detailResult.cookieText().isBlank()) {
                    cookieStr = detailResult.cookieText();
                }
                
                progress.completedCount++;
                if (detailResult.success()) {
                    progress.successCount++;
                } else {
                    progress.failedCount++;
                }

                progress.message = detailResult.message() != null
                        ? detailResult.message()
                        : String.format("同步进度: %d/%d", progress.completedCount, progress.totalCount);
                if (detailResult.needManual()) {
                    progress.message = "同步暂停: 需要完成滑块验证后重试";
                    break;
                }
            }

            progress.isCompleted = true;
            progress.isRunning = false;
            progress.currentItemId = null;
            if (!progress.message.startsWith("同步暂停")) {
                progress.message = String.format("同步完成: 成功%d, 失败%d", progress.successCount, progress.failedCount);
            }

        } catch (Exception e) {
            log.error("异步同步异常: syncId={}", syncId, e);
            progress.isCompleted = true;
            progress.isRunning = false;
            progress.message = "同步失败: " + e.getMessage();
        } finally {
            accountSyncMap.remove(accountId);
        }
    }

    private String resolveItemId(ItemDTO item) {
        return item.getDetailParams() != null ? item.getDetailParams().getItemId() : item.getId();
    }

    private int resolveDelayMillis(ItemDTO item) {
        int baseDelay = isSoldItem(item) ? SOLD_BASE_DELAY_MS : ON_SALE_BASE_DELAY_MS;
        int jitter = isSoldItem(item) ? SOLD_JITTER_MS : ON_SALE_JITTER_MS;
        return baseDelay + ThreadLocalRandom.current().nextInt(jitter + 1);
    }

    private boolean isSoldItem(ItemDTO item) {
        return item != null && Objects.equals(item.getItemStatus(), STATUS_SOLD);
    }

    private DetailFetchResult fetchAndSaveDetail(Long accountId, String itemId, String cookieStr) {
        try {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("itemId", itemId);

            XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
            request.setAccountId(accountId);
            request.setOperationName("商品详情同步");
            request.setApiName("mtop.taobao.idle.pc.detail");
            request.setDataMap(dataMap);
            request.setCookie(cookieStr);
            XianyuApiRecoveryResult apiResult = xianyuApiRecoveryService.callApi(request);

            if (!apiResult.isSuccess()) {
                log.warn("获取商品详情失败: itemId={}, error={}", itemId, apiResult.getErrorMessage());
                return new DetailFetchResult(false, null, buildFailureMessage(apiResult), needManual(apiResult));
            }

            String extractedDesc = ItemDetailUtils.extractDescFromDetailJson(apiResult.getResponse());
            
            if (extractedDesc != null && !extractedDesc.isEmpty()) {
                goodsInfoService.updateDetailInfo(itemId, extractedDesc);
                log.debug("商品详情同步成功: itemId={}", itemId);
                return new DetailFetchResult(true, apiResult.getCookieText(), null, false);
            } else {
                log.warn("商品详情提取失败: itemId={}", itemId);
                return new DetailFetchResult(false, apiResult.getCookieText(), null, false);
            }

        } catch (Exception e) {
            log.error("获取商品详情异常: itemId={}", itemId, e);
            return new DetailFetchResult(false, null, "商品详情同步失败: " + e.getMessage(), false);
        }
    }

    private String buildFailureMessage(XianyuApiRecoveryResult apiResult) {
        if (needManual(apiResult)) {
            return "商品详情同步需要完成滑块验证后重试";
        }
        return null;
    }

    private boolean needManual(XianyuApiRecoveryResult apiResult) {
        return apiResult != null && apiResult.getRecoveryResult() != null
                && apiResult.getRecoveryResult().isNeedManual();
    }

    private record DetailFetchResult(boolean success, String cookieText, String message, boolean needManual) {
    }

    @Override
    public SyncProgressRespDTO getProgress(String syncId) {
        SyncProgress progress = progressMap.get(syncId);
        if (progress == null) {
            return null;
        }

        SyncProgressRespDTO dto = new SyncProgressRespDTO();
        dto.setSyncId(progress.syncId);
        dto.setAccountId(progress.accountId);
        dto.setTotalCount(progress.totalCount);
        dto.setCompletedCount(progress.completedCount);
        dto.setSuccessCount(progress.successCount);
        dto.setFailedCount(progress.failedCount);
        dto.setIsCompleted(progress.isCompleted);
        dto.setIsRunning(progress.isRunning);
        dto.setCurrentItemId(progress.currentItemId);
        dto.setMessage(progress.message);
        dto.setStartTime(progress.startTime);

        if (progress.completedCount > 0 && progress.totalCount > 0) {
            long elapsed = System.currentTimeMillis() - progress.startTime;
            long avgTimePerItem = elapsed / progress.completedCount;
            long remainingItems = progress.totalCount - progress.completedCount;
            dto.setEstimatedRemainingTime(avgTimePerItem * remainingItems);
        }

        return dto;
    }

    @Override
    public void cancelSync(String syncId) {
        SyncProgress progress = progressMap.get(syncId);
        if (progress != null) {
            progress.cancelled = true;
            progress.message = "正在取消同步...";
            log.info("取消同步: syncId={}", syncId);
        }
    }

    @Override
    public boolean isSyncing(Long accountId) {
        String syncId = accountSyncMap.get(accountId);
        if (syncId == null) {
            return false;
        }
        SyncProgress progress = progressMap.get(syncId);
        return progress != null && progress.isRunning && !progress.isCompleted;
    }
}
