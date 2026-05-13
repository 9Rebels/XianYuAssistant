package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.OrderAutoRefreshService;
import com.feijimiao.xianyuassistant.service.OrderBatchRefreshService;
import com.feijimiao.xianyuassistant.service.OrderDetailUpdateService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderAutoRefreshServiceImpl implements OrderAutoRefreshService {

    private static final long REFRESH_DELAY_MINUTES = 5L;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private OrderBatchRefreshService orderBatchRefreshService;

    @Autowired
    private OrderDetailUpdateService orderDetailUpdateService;

    private ScheduledExecutorService scheduler;

    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "order-auto-refresh-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        log.info("订单自动刷新调度器初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        pendingTasks.forEach((key, future) -> {
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }
        });
        pendingTasks.clear();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public void scheduleRefresh(Long accountId, String orderId) {
        String normalizedOrderId = normalize(orderId);
        if (accountId == null || normalizedOrderId == null) {
            return;
        }
        String taskKey = buildTaskKey(accountId, normalizedOrderId);

        pendingTasks.compute(taskKey, (key, existing) -> {
            if (existing != null && !existing.isDone()) {
                log.debug("订单自动刷新任务已存在: accountId={}, orderId={}", accountId, normalizedOrderId);
                return existing;
            }
            log.info("订单自动刷新任务已提交: accountId={}, orderId={}, delay={}min",
                    accountId, normalizedOrderId, REFRESH_DELAY_MINUTES);
            return scheduler.schedule(
                    () -> refreshSingleOrder(key, accountId, normalizedOrderId),
                    REFRESH_DELAY_MINUTES,
                    TimeUnit.MINUTES);
        });
    }

    @Override
    public int getPendingTaskCount() {
        return pendingTasks.size();
    }

    void refreshSingleOrder(String taskKey, Long accountId, String orderId) {
        try {
            XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
            if (cookie == null || cookie.getCookieText() == null || cookie.getCookieText().isBlank()) {
                log.warn("订单自动刷新跳过，账号Cookie不存在: accountId={}, orderId={}", accountId, orderId);
                return;
            }

            List<OrderBatchRefreshService.OrderDetailResult> results =
                    orderBatchRefreshService.batchRefreshOrders(accountId, cookie.getCookieText(), List.of(orderId), true);
            for (OrderBatchRefreshService.OrderDetailResult result : results) {
                if (result.isSuccess()) {
                    orderDetailUpdateService.upsertOrderDetail(accountId, result);
                    log.info("订单自动刷新成功: accountId={}, orderId={}", accountId, result.getOrderId());
                } else {
                    log.warn("订单自动刷新失败: accountId={}, orderId={}, error={}",
                            accountId, result.getOrderId(), result.getError());
                }
            }
        } catch (Exception e) {
            log.error("订单自动刷新异常: accountId={}, orderId={}", accountId, orderId, e);
        } finally {
            pendingTasks.remove(taskKey);
        }
    }

    private String buildTaskKey(Long accountId, String orderId) {
        return accountId + ":" + orderId;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
