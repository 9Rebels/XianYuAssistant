package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBatchRefreshServiceTest {

    @Test
    void batchRefreshUsesResponseWaiterAndParsesOrderDetail() {
        OrderBatchRefreshService service = new OrderBatchRefreshService();
        BrowserPool browserPool = mock(BrowserPool.class);
        Page page = mock(Page.class);
        Response response = mock(Response.class);

        ReflectionTestUtils.setField(service, "browserPool", browserPool);
        when(browserPool.getBrowser(anyLong(), anyString(), anyBoolean(), anyBoolean()))
            .thenReturn(browserInstance(page));
        when(page.isClosed()).thenReturn(false);
        when(response.body()).thenReturn(orderDetailResponse().getBytes());
        when(page.waitForResponse(any(Predicate.class), any(Page.WaitForResponseOptions.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable trigger = invocation.getArgument(2);
                trigger.run();
                return response;
            });

        List<OrderBatchRefreshService.OrderDetailResult> results =
            service.batchRefreshOrders(1L, "cookie", List.of("3299380718224007985"), true);

        assertEquals(1, results.size());
        OrderBatchRefreshService.OrderDetailResult result = results.get(0);
        assertTrue(result.isSuccess());
        assertEquals("3299380718224007985", result.getOrderId());
        assertEquals(3, result.getOrderStatus());
        assertEquals("宝贝已发出，买家还没确认收货", result.getStatusText());
        assertEquals("1048981205041", result.getXyGoodsId());
        assertEquals("测试", result.getItemTitle());
        assertEquals("0.01", result.getPrice());
        assertEquals(1L, result.getOrderAmount());
        assertEquals("tbNick_8td3v", result.getBuyerUserName());
        assertEquals("2206529467253", result.getBuyerUserId());
        assertEquals(1777949766000L, result.getOrderCreateTime());
        assertEquals(1777949987000L, result.getOrderPayTime());
        assertEquals(1777960108000L, result.getOrderDeliveryTime());
        assertEquals("李嘉欣", result.getReceiverName());
        assertEquals("13070197800", result.getReceiverPhone());
        assertEquals("北京北京市海淀区马连洼街道圆明园西路2号", result.getReceiverAddress());
        assertEquals("北京市", result.getReceiverCity());

        verify(page).waitForResponse(any(Predicate.class), any(Page.WaitForResponseOptions.class), any(Runnable.class));
        verify(page, never()).route(anyString(), any(Consumer.class));
    }

    @Test
    void batchRefreshProcessesSameAccountOrdersSequentially() {
        OrderBatchRefreshService service = new OrderBatchRefreshService();
        BrowserPool browserPool = mock(BrowserPool.class);
        AtomicInteger activeRefreshes = new AtomicInteger();
        AtomicInteger maxActiveRefreshes = new AtomicInteger();
        Page firstPage = orderPage(activeRefreshes, maxActiveRefreshes);
        Page secondPage = orderPage(activeRefreshes, maxActiveRefreshes);

        ReflectionTestUtils.setField(service, "browserPool", browserPool);
        when(browserPool.getBrowser(anyLong(), anyString(), anyBoolean(), anyBoolean()))
            .thenReturn(browserInstance(firstPage), browserInstance(secondPage));

        List<OrderBatchRefreshService.OrderDetailResult> results =
            service.batchRefreshOrders(1L, "cookie", List.of("order-1", "order-2"), true);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(OrderBatchRefreshService.OrderDetailResult::isSuccess));
        assertEquals(1, maxActiveRefreshes.get());

        Page[] pages = {firstPage, secondPage};
        for (Page page : pages) {
            verify(page).waitForResponse(any(Predicate.class), any(Page.WaitForResponseOptions.class), any(Runnable.class));
        }
    }

    @Test
    void batchRefreshKeepsOrderIdWhenBrowserFails() {
        OrderBatchRefreshService service = new OrderBatchRefreshService();
        BrowserPool browserPool = mock(BrowserPool.class);

        ReflectionTestUtils.setField(service, "browserPool", browserPool);
        when(browserPool.getBrowser(anyLong(), anyString(), anyBoolean(), anyBoolean()))
            .thenThrow(new RuntimeException("browser unavailable"));

        List<OrderBatchRefreshService.OrderDetailResult> results =
            service.batchRefreshOrders(1L, "cookie", List.of("3299392347972000185"), true);

        assertEquals(1, results.size());
        OrderBatchRefreshService.OrderDetailResult result = results.get(0);
        assertFalse(result.isSuccess());
        assertEquals("3299392347972000185", result.getOrderId());
        assertTrue(result.getError().contains("browser unavailable"));
    }

    @Test
    void browserInstanceRetainsHeadlessFlag() {
        BrowserPool.BrowserInstance headedInstance = new BrowserPool.BrowserInstance(
            mock(Playwright.class),
            mock(Browser.class),
            mock(BrowserContext.class),
            mock(Page.class),
            false
        );

        assertFalse(headedInstance.isHeadless());
    }

    private BrowserPool.BrowserInstance browserInstance(Page page) {
        return new BrowserPool.BrowserInstance(
            mock(Playwright.class),
            mock(Browser.class),
            mock(BrowserContext.class),
            page
        );
    }

    private Page orderPage(AtomicInteger activeRefreshes, AtomicInteger maxActiveRefreshes) {
        Page page = mock(Page.class);
        Response response = mock(Response.class);
        when(page.isClosed()).thenReturn(false);
        when(response.body()).thenReturn(orderDetailResponse().getBytes());
        when(page.waitForResponse(any(Predicate.class), any(Page.WaitForResponseOptions.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                int active = activeRefreshes.incrementAndGet();
                maxActiveRefreshes.updateAndGet(previous -> Math.max(previous, active));
                try {
                    Thread.sleep(50);
                    Runnable trigger = invocation.getArgument(2);
                    trigger.run();
                    return response;
                } finally {
                    activeRefreshes.decrementAndGet();
                }
            });
        return page;
    }

    private String orderDetailResponse() {
        return """
            {
              "ret": ["SUCCESS::调用成功"],
              "data": {
                "status": "3",
                "peerUserId": "2206529467253",
                "itemId": "1048981205041",
                "utArgs": {
                  "orderStatusName": "宝贝已发出，买家还没确认收货"
                },
                "components": [
                  {
                    "render": "orderInfoVO",
                    "data": {
                      "itemInfo": {
                        "title": "测试"
                      },
                      "priceInfo": {
                        "amount": {
                          "value": "0.01"
                        }
                      },
                      "orderInfoList": [
                        { "title": "买家昵称", "value": "tbNick_8td3v" },
                        { "title": "下单时间", "value": "2026-05-05 10:56:06" },
                        { "title": "付款时间", "value": "2026-05-05 10:59:47" },
                        { "title": "发货时间", "value": "2026-05-05 13:48:28" }
                      ]
                    }
                  },
                  {
                    "render": "cainiaoLogisticsInfoVO",
                    "data": {
                      "addressInfoVO": {
                        "name": "李嘉欣",
                        "phoneNumber": "13070197800",
                        "address": "北京北京市海淀区马连洼街道圆明园西路2号"
                      }
                    }
                  }
                ]
              }
            }
            """;
    }
}
