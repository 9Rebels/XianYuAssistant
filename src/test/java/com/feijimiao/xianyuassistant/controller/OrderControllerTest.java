package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.OrderBatchRefreshService;
import com.feijimiao.xianyuassistant.service.OrderDetailUpdateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    @Test
    void batchRefreshChecksOrderByAccountAndOrderId() {
        OrderController controller = new OrderController();
        XianyuOrderMapper orderMapper = mock(XianyuOrderMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        OrderBatchRefreshService refreshService = mock(OrderBatchRefreshService.class);
        OrderDetailUpdateService detailUpdateService = mock(OrderDetailUpdateService.class);
        ReflectionTestUtils.setField(controller, "xianyuOrderMapper", orderMapper);
        ReflectionTestUtils.setField(controller, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(controller, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(controller, "orderBatchRefreshService", refreshService);
        ReflectionTestUtils.setField(controller, "orderDetailUpdateService", detailUpdateService);

        XianyuOrder order = new XianyuOrder();
        order.setXianyuAccountId(4L);
        order.setOrderId("5116115737001008015");
        when(orderMapper.selectByAccountIdAndOrderId(4L, "5116115737001008015"))
                .thenReturn(order);

        XianyuAccount account = new XianyuAccount();
        account.setId(4L);
        when(accountMapper.selectById(4L)).thenReturn(account);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("unb=2219250854984; _m_h5_tk=abc_123");
        when(cookieMapper.selectByAccountId(4L)).thenReturn(cookie);
        when(refreshService.batchRefreshOrders(
                4L, cookie.getCookieText(), List.of("5116115737001008015"), true))
                .thenReturn(List.of());

        ResultObject<Map<String, Object>> result = controller.batchRefresh(Map.of(
                "xianyuAccountId", 4L,
                "orderIds", List.of("5116115737001008015"),
                "headless", true
        ));

        assertEquals(200, result.getCode());
        assertEquals(0, result.getData().get("total"));
        verify(orderMapper).selectByAccountIdAndOrderId(4L, "5116115737001008015");
        verify(refreshService).batchRefreshOrders(
                4L, cookie.getCookieText(), List.of("5116115737001008015"), true);
    }
}
