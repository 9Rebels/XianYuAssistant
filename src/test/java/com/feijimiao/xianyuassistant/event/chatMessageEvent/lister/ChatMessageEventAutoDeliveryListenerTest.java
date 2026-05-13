package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuOrder;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.BargainFreeShippingService;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.OrderAutoRefreshService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageEventAutoDeliveryListenerTest {

    @Test
    void paymentMessageForNonAutoDeliveryGoodsStillCreatesOrderRecords() {
        Fixture fixture = new Fixture();

        XianyuGoodsInfo goodsInfo = new XianyuGoodsInfo();
        goodsInfo.setId(99L);
        goodsInfo.setXyGoodId("1048981205041");
        goodsInfo.setTitle("重生之我在某鱼卖东西");
        when(fixture.goodsInfoService.ensurePlaceholderGoods(eq(1L), eq("1048981205041"), eq("https://example.test/item?itemId=1048981205041"), any()))
                .thenReturn(goodsInfo);

        XianyuGoodsConfig goodsConfig = new XianyuGoodsConfig();
        goodsConfig.setXianyuAutoDeliveryOn(0);
        when(fixture.goodsConfigMapper.selectByAccountAndGoodsId(1L, "1048981205041")).thenReturn(goodsConfig);
        when(fixture.orderMapper.selectLatestByOrderId(1L, "1048981205041", "4502258607179022847")).thenReturn(null);
        when(fixture.xianyuOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(fixture.orderMapper.insert(any(XianyuGoodsOrder.class))).thenAnswer(invocation -> {
            XianyuGoodsOrder record = invocation.getArgument(0);
            record.setId(123L);
            return 1;
        });
        when(fixture.xianyuOrderMapper.insert(any(XianyuOrder.class))).thenReturn(1);

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, paymentMessage()));

        verify(fixture.orderMapper).insert(any(XianyuGoodsOrder.class));
        verify(fixture.xianyuOrderMapper).insert(any(XianyuOrder.class));
        verify(fixture.orderAutoRefreshService).scheduleRefresh(1L, "4502258607179022847");
        verify(fixture.autoDeliveryService, never()).executeDelivery(any(), any(), any(), any(), any(), any(), eq(true), anyInt());
    }

    @Test
    void bargainSettingPromptDoesNotCreateAutoDeliveryRecord() {
        Fixture fixture = new Fixture();

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, bargainSettingPromptMessage()));

        verifyNoDeliveryFlow(fixture);
    }

    @Test
    void plainBargainTextDoesNotCreateAutoDeliveryRecord() {
        Fixture fixture = new Fixture();

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, plainBargainMessage()));

        verifyNoDeliveryFlow(fixture);
    }

    @Test
    void waitingBargainSystemCardOnlyCallsFreeShipping() {
        Fixture fixture = new Fixture();
        enableBargainRule(fixture);
        BargainFreeShippingService.FreeShippingRequest request =
                new BargainFreeShippingService.FreeShippingRequest(1L, "4502258607179022847", "1048981205041", "buyer-1");
        when(fixture.bargainFreeShippingService.freeShipping(request))
                .thenReturn(new BargainFreeShippingService.FreeShippingResult(true, "SUCCESS::调用成功", "{}"));

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, bargainCardMessage("我已小刀，待刀成", true)));

        verify(fixture.bargainFreeShippingService).freeShipping(request);
        verify(fixture.orderMapper, never()).insert(any(XianyuGoodsOrder.class));
        verify(fixture.autoDeliveryService, never()).executeDelivery(any(), any(), any(), any(), any(), any(), eq(true), anyInt());
    }

    @Test
    void waitingBargainSystemCardSkipsFreeShippingWhenBargainRuleDisabled() {
        Fixture fixture = new Fixture();
        XianyuGoodsConfig goodsConfig = new XianyuGoodsConfig();
        goodsConfig.setXianyuAutoDeliveryOn(1);
        when(fixture.goodsConfigMapper.selectByAccountAndGoodsId(1L, "1048981205041")).thenReturn(goodsConfig);
        XianyuGoodsAutoDeliveryConfig rule = new XianyuGoodsAutoDeliveryConfig();
        rule.setEnabled(1);
        rule.setTriggerBargainEnabled(0);
        when(fixture.autoDeliveryConfigMapper.findRulesByAccountIdAndGoodsId(1L, "1048981205041")).thenReturn(List.of(rule));

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, bargainCardMessage("我已小刀，待刀成", true)));

        verify(fixture.bargainFreeShippingService, never()).freeShipping(any());
        verify(fixture.orderMapper, never()).insert(any(XianyuGoodsOrder.class));
    }

    @Test
    void readyToShipSystemCardCreatesRecordAndExecutesDelivery() {
        Fixture fixture = new Fixture();
        XianyuGoodsInfo goodsInfo = goodsInfo();
        when(fixture.goodsInfoService.ensurePlaceholderGoods(eq(1L), eq("1048981205041"), any(), any())).thenReturn(goodsInfo);
        when(fixture.xianyuOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(fixture.xianyuOrderMapper.insert(any(XianyuOrder.class))).thenReturn(1);
        when(fixture.orderMapper.selectLatestByOrderId(1L, "1048981205041", "4502258607179022847")).thenReturn(null);
        when(fixture.orderMapper.insert(any(XianyuGoodsOrder.class))).thenAnswer(invocation -> {
            XianyuGoodsOrder record = invocation.getArgument(0);
            record.setId(321L);
            return 1;
        });
        enableBargainRule(fixture);

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, bargainCardMessage("我已成功小刀，待发货", true)));

        verify(fixture.orderMapper).insert(any(XianyuGoodsOrder.class));
        verify(fixture.autoDeliveryService).executeDelivery(321L, 1L, "1048981205041", "buyer-1@goofish",
                "4502258607179022847", "yeye", true, 1);
        verify(fixture.bargainFreeShippingService, never()).freeShipping(any());
    }

    @Test
    void forgedBargainCardDoesNotTrigger() {
        Fixture fixture = new Fixture();

        fixture.listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, bargainCardMessage("我已成功小刀，待发货", false)));

        verifyNoDeliveryFlow(fixture);
    }

    private ChatMessageData paymentMessage() {
        ChatMessageData message = new ChatMessageData();
        message.setXianyuAccountId(1L);
        message.setPnmId("pnm-1");
        message.setContentType(26);
        message.setMsgContent("[我已付款，等待你发货]");
        message.setXyGoodsId("1048981205041");
        message.setSId("buyer-1@goofish");
        message.setOrderId("4502258607179022847");
        message.setSenderUserId("buyer-1");
        message.setSenderUserName("yeye");
        message.setReminderUrl("https://example.test/item?itemId=1048981205041");
        message.setCompleteMsg("{\"sample\":true}");
        message.setMessageTime(1715000000000L);
        return message;
    }

    private ChatMessageData bargainSettingPromptMessage() {
        ChatMessageData message = new ChatMessageData();
        message.setXianyuAccountId(1L);
        message.setPnmId("pnm-bargain-setting");
        message.setContentType(1);
        message.setMsgContent("[不想宝贝被砍价?设置不砍价回复 ]");
        message.setXyGoodsId("1048981205041");
        message.setSId("buyer-1@goofish");
        message.setSenderUserId("buyer-1");
        message.setSenderUserName("yeye");
        message.setMessageTime(1715000000000L);
        return message;
    }

    private ChatMessageData plainBargainMessage() {
        ChatMessageData message = bargainSettingPromptMessage();
        message.setPnmId("pnm-plain-bargain");
        message.setMsgContent("老板能小刀吗，能讲价吗");
        return message;
    }

    private ChatMessageData bargainCardMessage(String title, boolean systemCard) {
        ChatMessageData message = paymentMessage();
        message.setPnmId("pnm-card-" + title);
        message.setContentType(6);
        message.setMsgContent("[卡片消息]");
        message.setCompleteMsg(completeCardMsg(title, systemCard));
        return message;
    }

    private String completeCardMsg(String title, boolean systemCard) {
        String cardJson = "{\"contentType\":6,\"dxCard\":{\"item\":{\"main\":{\"exContent\":{\"title\":\""
                + title + "\"}}}}}";
        return "{\"1\":{\"7\":" + (systemCard ? 1 : 2)
                + ",\"6\":{\"3\":{\"4\":" + (systemCard ? 6 : 1) + ",\"5\":"
                + quote(cardJson) + "}},\"10\":{\"bizTag\":\""
                + (systemCard ? "{\\\"taskName\\\":\\\"SECURITY\\\"}" : "{}") + "\"}}}";
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private XianyuGoodsInfo goodsInfo() {
        XianyuGoodsInfo goodsInfo = new XianyuGoodsInfo();
        goodsInfo.setId(99L);
        goodsInfo.setXyGoodId("1048981205041");
        goodsInfo.setTitle("测试商品");
        return goodsInfo;
    }

    private void enableBargainRule(Fixture fixture) {
        XianyuGoodsConfig goodsConfig = new XianyuGoodsConfig();
        goodsConfig.setXianyuAutoDeliveryOn(1);
        when(fixture.goodsConfigMapper.selectByAccountAndGoodsId(1L, "1048981205041")).thenReturn(goodsConfig);
        XianyuGoodsAutoDeliveryConfig rule = new XianyuGoodsAutoDeliveryConfig();
        rule.setEnabled(1);
        rule.setTriggerBargainEnabled(1);
        when(fixture.autoDeliveryConfigMapper.findRulesByAccountIdAndGoodsId(1L, "1048981205041")).thenReturn(List.of(rule));
    }

    private void verifyNoDeliveryFlow(Fixture fixture) {
        verify(fixture.goodsInfoService, never()).ensurePlaceholderGoods(any(), any(), any(), any());
        verify(fixture.orderMapper, never()).insert(any(XianyuGoodsOrder.class));
        verify(fixture.xianyuOrderMapper, never()).insert(any(XianyuOrder.class));
        verify(fixture.bargainFreeShippingService, never()).freeShipping(any());
        verify(fixture.autoDeliveryService, never()).executeDelivery(any(), any(), any(), any(), any(), any(), eq(true), anyInt());
    }

    private static class Fixture {
        private final XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        private final GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);
        private final XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        private final XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        private final AutoDeliveryService autoDeliveryService = mock(AutoDeliveryService.class);
        private final OrderAutoRefreshService orderAutoRefreshService = mock(OrderAutoRefreshService.class);
        private final XianyuOrderMapper xianyuOrderMapper = mock(XianyuOrderMapper.class);
        private final OperationLogService operationLogService = mock(OperationLogService.class);
        private final BargainFreeShippingService bargainFreeShippingService = mock(BargainFreeShippingService.class);
        private final ChatMessageEventAutoDeliveryListener listener = new ChatMessageEventAutoDeliveryListener();

        private Fixture() {
            ReflectionTestUtils.setField(listener, "orderMapper", orderMapper);
            ReflectionTestUtils.setField(listener, "goodsInfoService", goodsInfoService);
            ReflectionTestUtils.setField(listener, "goodsConfigMapper", goodsConfigMapper);
            ReflectionTestUtils.setField(listener, "autoDeliveryConfigMapper", autoDeliveryConfigMapper);
            ReflectionTestUtils.setField(listener, "autoDeliveryService", autoDeliveryService);
            ReflectionTestUtils.setField(listener, "orderAutoRefreshService", orderAutoRefreshService);
            ReflectionTestUtils.setField(listener, "xianyuOrderMapper", xianyuOrderMapper);
            ReflectionTestUtils.setField(listener, "operationLogService", operationLogService);
            ReflectionTestUtils.setField(listener, "bargainFreeShippingService", bargainFreeShippingService);
        }
    }
}
