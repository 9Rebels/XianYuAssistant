package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.config.WebSocketConfig;
import com.feijimiao.xianyuassistant.controller.dto.ManualReturnApiDeliveryReqDTO;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuOrderMapper;
import com.feijimiao.xianyuassistant.service.ApiDeliveryService;
import com.feijimiao.xianyuassistant.service.DeliveryMessageSettingService;
import com.feijimiao.xianyuassistant.service.EmailNotifyService;
import com.feijimiao.xianyuassistant.service.KamiConfigService;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.SentMessageSaveService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryContext;
import com.feijimiao.xianyuassistant.service.bo.ApiDeliveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class AutoDeliveryServiceImplTest {

    @Test
    void manualReturnApiDeliveryUsesRecordedRuleInsteadOfFirstGoodsRule() {
        AutoDeliveryServiceImpl service = new AutoDeliveryServiceImpl();
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuGoodsAutoDeliveryConfigMapper configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        ApiDeliveryService apiDeliveryService = mock(ApiDeliveryService.class);

        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);
        ReflectionTestUtils.setField(service, "apiDeliveryService", apiDeliveryService);

        ManualReturnApiDeliveryReqDTO reqDTO = new ManualReturnApiDeliveryReqDTO();
        reqDTO.setXianyuAccountId(1L);
        reqDTO.setRecordId(9L);
        reqDTO.setXyGoodsId("1049453061729");

        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setId(9L);
        record.setXianyuAccountId(1L);
        record.setXyGoodsId("1049453061729");
        record.setDeliveryMode(4);
        record.setRuleName("free9");
        record.setExternalAllocationId("dml_alloc_001");
        record.setExternalReturnState(0);
        record.setOrderId("ORDER-001");

        XianyuGoodsAutoDeliveryConfig wrongRule = new XianyuGoodsAutoDeliveryConfig();
        wrongRule.setId(1L);
        wrongRule.setXianyuAccountId(1L);
        wrongRule.setXyGoodsId("1049453061729");
        wrongRule.setDeliveryMode(4);
        wrongRule.setRuleName("half9");
        wrongRule.setApiReturnUrl("http://wrong.example/return");

        XianyuGoodsAutoDeliveryConfig matchedRule = new XianyuGoodsAutoDeliveryConfig();
        matchedRule.setId(2L);
        matchedRule.setXianyuAccountId(1L);
        matchedRule.setXyGoodsId("1049453061729");
        matchedRule.setDeliveryMode(4);
        matchedRule.setRuleName("free9");
        matchedRule.setApiReturnUrl("http://matched.example/return");

        XianyuGoodsOrder updatedRecord = new XianyuGoodsOrder();
        updatedRecord.setId(9L);
        updatedRecord.setExternalReturnState(1);
        updatedRecord.setExternalReturnReason("手动回库");

        when(orderMapper.selectByIdAndAccountAndGoods(9L, 1L, "1049453061729")).thenReturn(record);
        when(configMapper.findRulesByAccountIdAndGoodsId(1L, "1049453061729"))
                .thenReturn(List.of(wrongRule, matchedRule));
        when(apiDeliveryService.returnAllocation(any(), any()))
                .thenReturn(ApiDeliveryResult.of(true, null, null, "ok"));
        when(orderMapper.selectById(9L)).thenReturn(updatedRecord);

        ResultObject<String> result = service.manualReturnApiDelivery(reqDTO);

        assertEquals(200, result.getCode());
        assertEquals("手动回库成功", result.getData());
        verify(apiDeliveryService).returnAllocation(
                argThat(config -> config != null
                        && "free9".equals(config.getRuleName())
                        && "http://matched.example/return".equals(config.getApiReturnUrl())),
                any()
        );
    }

    @Test
    void kamiDeliverySendsOneMessagePerKamiWhenMultiKamiModeIsSplit() {
        Fixture fixture = new Fixture();
        when(fixture.sysSettingService.getSettingValue("delivery_multi_quantity_send_mode")).thenReturn("separate");
        XianyuGoodsAutoDeliveryConfig config = kamiConfig(2);
        when(fixture.configMapper.findRulesByAccountIdAndGoodsId(1L, "ITEM-1")).thenReturn(List.of(config));
        when(fixture.orderMapper.selectById(9L)).thenReturn(orderRecord());
        when(fixture.websocketService.sendMessageWithResult(anyLong(), any(), any(), any())).thenReturn(true);
        when(fixture.kamiConfigService.acquireKami(100L, "ORDER-1"))
                .thenReturn(kamiItem(101L, "K1"), kamiItem(102L, "K2"), kamiItem(103L, "K3"));

        fixture.service.executeDelivery(9L, 1L, "ITEM-1", "buyer-1@goofish", "ORDER-1", "buyer", false, 3);

        verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "卡密:K1");
        verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "卡密:K2");
        verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "卡密:K3");
        verify(fixture.configMapper).markDeliverySuccessBatch(8L, 3);
        verify(fixture.sentMessageSaveService, times(3)).saveAiAssistantReply(eq(1L), eq("buyer-1"), eq("buyer-1"), any(), eq("ITEM-1"));
    }

    @Test
    void kamiDeliveryFailsAndRollsBackWhenQuantityCannotBeFullyAllocated() {
        Fixture fixture = new Fixture();
        XianyuGoodsAutoDeliveryConfig config = kamiConfig(1);
        when(fixture.configMapper.findRulesByAccountIdAndGoodsId(1L, "ITEM-1")).thenReturn(List.of(config));
        when(fixture.orderMapper.selectById(9L)).thenReturn(orderRecord());
        when(fixture.kamiConfigService.acquireKami(100L, "ORDER-1"))
                .thenReturn(kamiItem(101L, "K1"), kamiItem(102L, "K2"), null);

        fixture.service.executeDelivery(9L, 1L, "ITEM-1", "buyer-1@goofish", "ORDER-1", "buyer", false, 3);

        verify(fixture.kamiConfigService).resetKamiItem(101L);
        verify(fixture.kamiConfigService).resetKamiItem(102L);
        verify(fixture.kamiUsageRecordMapper, never()).insert(any());
        verify(fixture.websocketService, never()).sendMessageWithResult(anyLong(), any(), any(), any());
        verify(fixture.configMapper, never()).markDeliverySuccessBatch(anyLong(), any(Integer.class));
        verify(fixture.orderMapper).updateStateContentAndFailReason(9L, -1, null, "卡密库存不足，无可用卡密");
    }

    @Test
    void textDeliverySendsOneMessagePerQuantityWhenSettingIsSeparate() {
        Fixture fixture = new Fixture();
        when(fixture.sysSettingService.getSettingValue("delivery_multi_quantity_send_mode")).thenReturn("separate");
        XianyuGoodsAutoDeliveryConfig config = baseConfig(1);
        config.setAutoDeliveryContent("发货文本");
        when(fixture.configMapper.findRulesByAccountIdAndGoodsId(1L, "ITEM-1")).thenReturn(List.of(config));
        when(fixture.orderMapper.selectById(9L)).thenReturn(orderRecord());
        when(fixture.websocketService.sendMessageWithResult(anyLong(), any(), any(), any())).thenReturn(true);

        fixture.service.executeDelivery(9L, 1L, "ITEM-1", "buyer-1@goofish", "ORDER-1", "buyer", false, 3);

        verify(fixture.websocketService, times(3)).sendMessageWithResult(1L, "buyer-1", "buyer-1", "发货文本");
        verify(fixture.configMapper).markDeliverySuccessBatch(8L, 3);
        verify(fixture.sentMessageSaveService, times(3)).saveAiAssistantReply(eq(1L), eq("buyer-1"), eq("buyer-1"), eq("发货文本"), eq("ITEM-1"));
    }

    @Test
    void postDeliveryTextIsSentAfterMainDeliveryContent() {
        Fixture fixture = new Fixture();
        XianyuGoodsAutoDeliveryConfig config = baseConfig(1);
        config.setAutoDeliveryContent("发货文本");
        config.setPostDeliveryText("补充说明");
        when(fixture.configMapper.findRulesByAccountIdAndGoodsId(1L, "ITEM-1")).thenReturn(List.of(config));
        when(fixture.orderMapper.selectById(9L)).thenReturn(orderRecord());
        when(fixture.websocketService.sendMessageWithResult(anyLong(), any(), any(), any())).thenReturn(true);

        fixture.service.executeDelivery(9L, 1L, "ITEM-1", "buyer-1@goofish", "ORDER-1", "buyer", false, 1);

        var inOrder = inOrder(fixture.websocketService);
        inOrder.verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "发货文本");
        inOrder.verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "补充说明");
        verify(fixture.configMapper).markDeliverySuccessBatch(8L, 1);
        verify(fixture.sentMessageSaveService).saveAiAssistantReply(1L, "buyer-1", "buyer-1", "发货文本", "ITEM-1");
        verify(fixture.sentMessageSaveService).saveAiAssistantReply(1L, "buyer-1", "buyer-1", "补充说明", "ITEM-1");
    }

    @Test
    void postDeliveryTextFailureMarksDeliveryFailedAfterMainContent() {
        Fixture fixture = new Fixture();
        XianyuGoodsAutoDeliveryConfig config = baseConfig(4);
        config.setApiAllocateUrl("https://api.example/allocate");
        config.setPostDeliveryText("补充说明");
        when(fixture.configMapper.findRulesByAccountIdAndGoodsId(1L, "ITEM-1")).thenReturn(List.of(config));
        when(fixture.orderMapper.selectById(9L)).thenReturn(orderRecord());
        when(fixture.apiDeliveryService.allocate(eq(config), any(ApiDeliveryContext.class)))
                .thenReturn(ApiDeliveryResult.of(true, "接口内容", "ALLOC-1", "ok"));
        when(fixture.websocketService.sendMessageWithResult(1L, "buyer-1", "buyer-1", "接口内容")).thenReturn(true);
        when(fixture.websocketService.sendMessageWithResult(1L, "buyer-1", "buyer-1", "补充说明")).thenReturn(false);

        fixture.service.executeDelivery(9L, 1L, "ITEM-1", "buyer-1@goofish", "ORDER-1", "buyer", false, 1);

        verify(fixture.orderMapper).updateStateContentAndFailReason(9L, -1, "接口内容", "发货后文本发送失败");
        verify(fixture.configMapper, never()).markDeliverySuccessBatch(anyLong(), any(Integer.class));
        verify(fixture.apiDeliveryService, never()).confirm(eq(config), any(ApiDeliveryContext.class));
        verify(fixture.apiDeliveryService, never()).returnAllocation(eq(config), any(ApiDeliveryContext.class));
    }

    @Test
    void apiDeliveryAllocatesOncePerQuantityAndPassesQuantityContext() {
        Fixture fixture = new Fixture();
        when(fixture.sysSettingService.getSettingValue("delivery_multi_quantity_send_mode")).thenReturn("separate");
        XianyuGoodsAutoDeliveryConfig config = baseConfig(4);
        config.setApiAllocateUrl("https://api.example/allocate");
        config.setApiDeliveryTemplate("内容:{apiContent}");
        when(fixture.configMapper.findRulesByAccountIdAndGoodsId(1L, "ITEM-1")).thenReturn(List.of(config));
        when(fixture.orderMapper.selectById(9L)).thenReturn(orderRecord());
        when(fixture.websocketService.sendMessageWithResult(anyLong(), any(), any(), any())).thenReturn(true);
        when(fixture.apiDeliveryService.allocate(eq(config), argThat(context -> context != null && context.getDeliveryIndex() == 1
                && context.getDeliveryTotal() == 3 && context.getBuyQuantity() == 3)))
                .thenReturn(ApiDeliveryResult.of(true, "A", "ALLOC-1", "ok"));
        when(fixture.apiDeliveryService.allocate(eq(config), argThat(context -> context != null && context.getDeliveryIndex() == 2
                && context.getDeliveryTotal() == 3 && context.getBuyQuantity() == 3)))
                .thenReturn(ApiDeliveryResult.of(true, "B", "ALLOC-2", "ok"));
        when(fixture.apiDeliveryService.allocate(eq(config), argThat(context -> context != null && context.getDeliveryIndex() == 3
                && context.getDeliveryTotal() == 3 && context.getBuyQuantity() == 3)))
                .thenReturn(ApiDeliveryResult.of(true, "C", "ALLOC-3", "ok"));
        when(fixture.apiDeliveryService.confirm(eq(config), any(ApiDeliveryContext.class)))
                .thenReturn(ApiDeliveryResult.of(true, null, null, "ok"));

        fixture.service.executeDelivery(9L, 1L, "ITEM-1", "buyer-1@goofish", "ORDER-1", "buyer", false, 3);

        verify(fixture.orderMapper).updateExternalAllocation(9L, "ALLOC-1,ALLOC-2,ALLOC-3");
        verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "内容:A");
        verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "内容:B");
        verify(fixture.websocketService).sendMessageWithResult(1L, "buyer-1", "buyer-1", "内容:C");
        verify(fixture.apiDeliveryService, times(3)).confirm(eq(config), any(ApiDeliveryContext.class));
        verify(fixture.configMapper).markDeliverySuccessBatch(8L, 3);
    }

    private XianyuGoodsAutoDeliveryConfig kamiConfig(int multiKamiMode) {
        XianyuGoodsAutoDeliveryConfig config = baseConfig(2);
        config.setKamiConfigIds("100");
        config.setKamiDeliveryTemplate("卡密:{kmKey}");
        config.setMultiKamiMode(multiKamiMode);
        return config;
    }

    private XianyuGoodsAutoDeliveryConfig baseConfig(int deliveryMode) {
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setId(8L);
        config.setXianyuAccountId(1L);
        config.setXyGoodsId("ITEM-1");
        config.setDeliveryMode(deliveryMode);
        config.setEnabled(1);
        config.setStock(-1);
        return config;
    }

    private XianyuGoodsOrder orderRecord() {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setId(9L);
        record.setXianyuAccountId(1L);
        record.setXyGoodsId("ITEM-1");
        record.setSid("buyer-1@goofish");
        record.setOrderId("ORDER-1");
        return record;
    }

    private XianyuKamiItem kamiItem(Long id, String content) {
        XianyuKamiItem item = new XianyuKamiItem();
        item.setId(id);
        item.setKamiConfigId(100L);
        item.setKamiContent(content);
        return item;
    }

    private static class Fixture {
        private final AutoDeliveryServiceImpl service = new AutoDeliveryServiceImpl();
        private final XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        private final XianyuGoodsAutoDeliveryConfigMapper configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        private final XianyuOrderMapper xianyuOrderMapper = mock(XianyuOrderMapper.class);
        private final WebSocketService websocketService = mock(WebSocketService.class);
        private final SentMessageSaveService sentMessageSaveService = mock(SentMessageSaveService.class);
        private final KamiConfigService kamiConfigService = mock(KamiConfigService.class);
        private final EmailNotifyService emailNotifyService = mock(EmailNotifyService.class);
        private final XianyuKamiUsageRecordMapper kamiUsageRecordMapper = mock(XianyuKamiUsageRecordMapper.class);
        private final DeliveryMessageSettingService deliveryMessageSettingService = mock(DeliveryMessageSettingService.class);
        private final NotificationService notificationService = mock(NotificationService.class);
        private final NotificationContentBuilder notificationContentBuilder = mock(NotificationContentBuilder.class);
        private final OperationLogService operationLogService = mock(OperationLogService.class);
        private final SysSettingService sysSettingService = mock(SysSettingService.class);
        private final ApiDeliveryService apiDeliveryService = mock(ApiDeliveryService.class);

        private Fixture() {
            WebSocketConfig webSocketConfig = new WebSocketConfig();
            webSocketConfig.setMessageRetryAttempts(1);
            ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
            ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);
            ReflectionTestUtils.setField(service, "xianyuOrderMapper", xianyuOrderMapper);
            ReflectionTestUtils.setField(service, "webSocketService", websocketService);
            ReflectionTestUtils.setField(service, "webSocketConfig", webSocketConfig);
            ReflectionTestUtils.setField(service, "sentMessageSaveService", sentMessageSaveService);
            ReflectionTestUtils.setField(service, "kamiConfigService", kamiConfigService);
            ReflectionTestUtils.setField(service, "emailNotifyService", emailNotifyService);
            ReflectionTestUtils.setField(service, "kamiUsageRecordMapper", kamiUsageRecordMapper);
            ReflectionTestUtils.setField(service, "deliveryMessageSettingService", deliveryMessageSettingService);
            ReflectionTestUtils.setField(service, "notificationService", notificationService);
            ReflectionTestUtils.setField(service, "notificationContentBuilder", notificationContentBuilder);
            ReflectionTestUtils.setField(service, "operationLogService", operationLogService);
            ReflectionTestUtils.setField(service, "sysSettingService", sysSettingService);
            ReflectionTestUtils.setField(service, "apiDeliveryService", apiDeliveryService);
            when(notificationContentBuilder.eventContent(any(), any(), any(), any())).thenReturn("content");
        }
    }
}
