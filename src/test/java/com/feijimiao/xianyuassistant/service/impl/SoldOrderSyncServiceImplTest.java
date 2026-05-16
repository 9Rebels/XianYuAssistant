package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.CookieRecoveryService;
import com.feijimiao.xianyuassistant.service.SellerSessionRefreshService;
import com.feijimiao.xianyuassistant.service.SoldOrderSyncService;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoldOrderSyncServiceImplTest {

    @Test
    void syncRejectsNonFishShopAccountBeforeRequestingApi() {
        SoldOrderSyncServiceImpl service = new SoldOrderSyncServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(1L);
        account.setFishShopUser(false);
        when(accountMapper.selectById(1L)).thenReturn(account);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.syncSoldOrders(1L)
        );

        assertEquals(SoldOrderSyncService.NON_FISH_SHOP_MESSAGE, error.getMessage());
    }

    @Test
    void saveOrdersFromSellerWorkbenchResponseInsertsOrder() {
        SoldOrderSyncServiceImpl service = new SoldOrderSyncServiceImpl();
        XianyuOrderMapper orderMapper = mock(XianyuOrderMapper.class);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("2219250854984");
        account.setDisplayName("重生之我在某鱼卖东西");

        SoldOrderSyncServiceImpl.PageSaveResult result =
                service.saveOrdersFromResponse(account, sellerOrderResponse());

        ArgumentCaptor<XianyuOrder> orderCaptor = ArgumentCaptor.forClass(XianyuOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        XianyuOrder order = orderCaptor.getValue();

        assertEquals(1, result.itemCount());
        assertEquals(1, result.insertedCount());
        assertEquals(87, result.totalCount());
        assertTrue(result.nextPage());
        assertEquals("3300351279401008157", order.getOrderId());
        assertEquals("907625879418", order.getXyGoodsId());
        assertEquals("茅台官方保温杯全新带礼袋，350ml大容量，316不锈钢弹跳", order.getGoodsTitle());
        assertEquals("1728685717", order.getBuyerUserId());
        assertEquals("老酒醉人心", order.getBuyerUserName());
        assertEquals("2219250854984", order.getSellerUserId());
        assertEquals("重生之我在某鱼卖东西", order.getSellerUserName());
        assertEquals(2, order.getOrderStatus());
        assertEquals("待发货", order.getOrderStatusText());
        assertEquals(3900L, order.getOrderAmount());
        assertEquals("39.00", order.getOrderAmountText());
        assertEquals("袁生", order.getReceiverName());
        assertEquals("17506104888", order.getReceiverPhone());
        assertEquals("江苏省泰州市靖江市靖城街道合兴路金丝利零售仿古街店", order.getReceiverAddress());
        assertEquals("泰州市", order.getReceiverCity());
        assertEquals(1778154423000L, order.getOrderCreateTime());
        assertEquals(1778154611000L, order.getOrderPayTime());
        assertTrue(order.getCompleteMsg().contains("buyerInfoVO"));
        verify(orderMapper).selectByAccountIdAndOrderId(2L, "3300351279401008157");
    }

    @Test
    void saveOrdersSkipsOrderWhenGoodsBelongsToAnotherAccount() {
        SoldOrderSyncServiceImpl service = new SoldOrderSyncServiceImpl();
        XianyuOrderMapper orderMapper = mock(XianyuOrderMapper.class);
        XianyuGoodsInfoMapper goodsInfoMapper = mock(XianyuGoodsInfoMapper.class);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "goodsInfoMapper", goodsInfoMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(4L);
        account.setUnb("2219250854984");
        account.setDisplayName("杯子正品");
        when(goodsInfoMapper.selectOwnerAccountIdByGoodsId("907625879418")).thenReturn(3L);

        SoldOrderSyncServiceImpl.PageSaveResult result =
                service.saveOrdersFromResponse(account, sellerOrderResponse());

        assertEquals(0, result.itemCount());
        assertEquals(0, result.insertedCount());
        assertEquals(0, result.updatedCount());
        assertEquals(87, result.totalCount());
        verify(orderMapper, never()).insert(any(XianyuOrder.class));
        verify(orderMapper, never()).updateById(any(XianyuOrder.class));
    }

    @Test
    void syncRetriesOnceWithRecoveredCookieWhenSellerSessionExpired() {
        SoldOrderSyncServiceImpl service = spy(new SoldOrderSyncServiceImpl());
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuOrderMapper orderMapper = mock(XianyuOrderMapper.class);
        CookieRecoveryService cookieRecoveryService = mock(CookieRecoveryService.class);
        SellerSessionRefreshService sellerSessionRefreshService = mock(SellerSessionRefreshService.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "cookieRecoveryService", cookieRecoveryService);
        ReflectionTestUtils.setField(service, "sellerSessionRefreshService", sellerSessionRefreshService);

        XianyuAccount account = new XianyuAccount();
        account.setId(3L);
        account.setUnb("2219377228543");
        account.setFishShopUser(true);
        when(accountMapper.selectById(3L)).thenReturn(account);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("_m_h5_tk=old_1778864650767; unb=2219377228543");
        XianyuCookie latestCookie = new XianyuCookie();
        latestCookie.setCookieText("_m_h5_tk=db_1778864650767; unb=2219377228543");
        when(cookieMapper.selectByAccountId(3L)).thenReturn(cookie, latestCookie);
        when(cookieRecoveryService.recover(
                3L, "同步卖家订单列表", "卖家订单接口 Session 过期"))
                .thenReturn(CookieRecoveryResult.success(
                        "_m_h5_tk=recovery_1778864650767; unb=2219377228543",
                        "Cookie已自动刷新"));
        when(sellerSessionRefreshService.refreshSellerSession(
                3L, "_m_h5_tk=db_1778864650767; unb=2219377228543"))
                .thenReturn(CookieRecoveryResult.success(
                        "_m_h5_tk=seller_1778864650767; unb=2219377228543; seller_session=ok",
                        "卖家订单接口 Session 已通过浏览器激活"));
        when(orderMapper.selectByAccountIdAndOrderId(3L, "3300351279401008157")).thenReturn(null);
        org.mockito.Mockito.doReturn(sessionExpiredResponse())
                .doReturn(singlePageSellerOrderResponse())
                .when(service).requestSoldOrderPage(
                        eq(account), org.mockito.Mockito.anyString(), eq(1), eq("0"));

        SoldOrderSyncService.SyncResult result = service.syncSoldOrders(3L);

        assertEquals(1, result.getFetchedCount());
        verify(cookieRecoveryService).recover(
                3L, "同步卖家订单列表", "卖家订单接口 Session 过期");
        verify(sellerSessionRefreshService).refreshSellerSession(
                3L, "_m_h5_tk=db_1778864650767; unb=2219377228543");
        verify(service).requestSoldOrderPage(
                eq(account), eq("_m_h5_tk=old_1778864650767; unb=2219377228543"), eq(1), eq("0"));
        verify(service).requestSoldOrderPage(
                eq(account), eq("_m_h5_tk=seller_1778864650767; unb=2219377228543; seller_session=ok"), eq(1), eq("0"));
    }

    @Test
    void syncStopsWhenSellerSessionActivationStillFails() {
        SoldOrderSyncServiceImpl service = spy(new SoldOrderSyncServiceImpl());
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        XianyuOrderMapper orderMapper = mock(XianyuOrderMapper.class);
        CookieRecoveryService cookieRecoveryService = mock(CookieRecoveryService.class);
        SellerSessionRefreshService sellerSessionRefreshService = mock(SellerSessionRefreshService.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "cookieRecoveryService", cookieRecoveryService);
        ReflectionTestUtils.setField(service, "sellerSessionRefreshService", sellerSessionRefreshService);

        XianyuAccount account = new XianyuAccount();
        account.setId(3L);
        account.setUnb("2219377228543");
        account.setFishShopUser(true);
        when(accountMapper.selectById(3L)).thenReturn(account);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setCookieText("_m_h5_tk=old_1778864650767; unb=2219377228543");
        XianyuCookie latestCookie = new XianyuCookie();
        latestCookie.setCookieText("_m_h5_tk=new_1778864650767; unb=2219377228543");
        when(cookieMapper.selectByAccountId(3L)).thenReturn(cookie, latestCookie);
        when(cookieRecoveryService.recover(
                3L, "同步卖家订单列表", "卖家订单接口 Session 过期"))
                .thenReturn(CookieRecoveryResult.success(
                        "_m_h5_tk=new_1778864650767; unb=2219377228543",
                        "Cookie已自动刷新"));
        when(sellerSessionRefreshService.refreshSellerSession(
                3L, "_m_h5_tk=new_1778864650767; unb=2219377228543"))
                .thenReturn(CookieRecoveryResult.failed(
                        "卖家订单接口 Session 激活后仍过期，不代表连接管理Cookie无效"));
        org.mockito.Mockito.doReturn(sessionExpiredResponse())
                .when(service).requestSoldOrderPage(
                        eq(account), org.mockito.Mockito.anyString(), eq(1), eq("0"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.syncSoldOrders(3L)
        );

        assertTrue(error.getMessage().contains("卖家订单接口 Session 激活后仍过期"));
        verify(service, times(1)).requestSoldOrderPage(
                eq(account), eq("_m_h5_tk=old_1778864650767; unb=2219377228543"), eq(1), eq("0"));
        verify(sellerSessionRefreshService).refreshSellerSession(
                3L, "_m_h5_tk=new_1778864650767; unb=2219377228543");
    }

    private String sellerOrderResponse() {
        return """
                {
                  "api": "mtop.taobao.idle.trade.merchant.sold.get",
                  "data": {
                    "module": {
                      "items": [
                        {
                          "buyerInfoVO": {
                            "address": "江苏省泰州市靖江市靖城街道合兴路金丝利零售仿古街店",
                            "buyerId": "1728685717",
                            "name": "袁生",
                            "phone": "17506104888",
                            "userNick": "老酒醉人心"
                          },
                          "commonData": {
                            "createTime": "2026-05-07 19:47:03",
                            "itemId": "907625879418",
                            "orderId": "3300351279401008157",
                            "orderStatus": "待发货",
                            "paySuccessTime": "2026-05-07 19:50:11"
                          },
                          "itemVO": {
                            "title": "茅台官方保温杯全新带礼袋，350ml大容量，316不锈钢弹跳"
                          },
                          "priceVO": {
                            "totalPrice": "39.00"
                          }
                        }
                      ],
                      "lastEndRow": "20",
                      "nextPage": "true",
                      "totalCount": "87"
                    }
                  },
                  "ret": ["SUCCESS::调用成功"],
                  "v": "1.0"
                }
                """;
    }

    private String sessionExpiredResponse() {
        return """
                {
                  "api": "mtop.taobao.idle.trade.merchant.sold.get",
                  "ret": ["FAIL_SYS_SESSION_EXPIRED::Session过期"],
                  "v": "1.0"
                }
                """;
    }

    private String singlePageSellerOrderResponse() {
        return sellerOrderResponse()
                .replace("\"nextPage\": \"true\"", "\"nextPage\": \"false\"")
                .replace("\"totalCount\": \"87\"", "\"totalCount\": \"1\"");
    }
}
