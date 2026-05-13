package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.OrderBatchRefreshService;
import com.feijimiao.xianyuassistant.service.OrderDetailUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderAutoRefreshServiceImplTest {

    @Test
    void scheduleRefreshDeduplicatesSameOrder() {
        OrderAutoRefreshServiceImpl service = new OrderAutoRefreshServiceImpl();
        service.init();
        try {
            service.scheduleRefresh(1L, "order-1");
            service.scheduleRefresh(1L, " order-1 ");

            assertEquals(1, service.getPendingTaskCount());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void refreshSingleOrderUsesCookieAndWritesSuccessfulResult() {
        OrderAutoRefreshServiceImpl service = new OrderAutoRefreshServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        OrderBatchRefreshService orderBatchRefreshService = mock(OrderBatchRefreshService.class);
        OrderDetailUpdateService orderDetailUpdateService = mock(OrderDetailUpdateService.class);
        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("cookie");
        OrderBatchRefreshService.OrderDetailResult result = new OrderBatchRefreshService.OrderDetailResult();
        result.setSuccess(true);
        result.setOrderId("order-1");

        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "orderBatchRefreshService", orderBatchRefreshService);
        ReflectionTestUtils.setField(service, "orderDetailUpdateService", orderDetailUpdateService);
        when(cookieMapper.selectByAccountId(1L)).thenReturn(cookie);
        when(orderBatchRefreshService.batchRefreshOrders(1L, "cookie", List.of("order-1"), true))
                .thenReturn(List.of(result));

        service.refreshSingleOrder("1:order-1", 1L, "order-1");

        verify(orderBatchRefreshService).batchRefreshOrders(1L, "cookie", List.of("order-1"), true);
        verify(orderDetailUpdateService).upsertOrderDetail(1L, result);
    }
}
