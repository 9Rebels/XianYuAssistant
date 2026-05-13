package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.SoldOrderSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        when(orderMapper.selectOne(any())).thenReturn(null);

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
}
