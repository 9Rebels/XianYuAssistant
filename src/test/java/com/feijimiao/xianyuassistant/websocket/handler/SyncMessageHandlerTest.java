package com.feijimiao.xianyuassistant.websocket.handler;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.service.ConversationReadStateService;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncMessageHandlerTest {

    @Test
    void messageWithDifferentReceiverIsNotPublishedForCurrentAccount() {
        SyncMessageHandler handler = new SyncMessageHandler();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("999999");
        when(accountMapper.selectById(2L)).thenReturn(account);

        ReflectionTestUtils.setField(handler, "eventPublisher", publisher);
        ReflectionTestUtils.setField(handler, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(handler, "readStateService", mock(ConversationReadStateService.class));

        ReflectionTestUtils.invokeMethod(
                handler,
                "parseAndPublishEvent",
                "2",
                messageJson("1986587151", "4046662982"),
                "/s/para"
        );

        verify(publisher, never()).publishEvent(any(ChatMessageReceivedEvent.class));
    }

    @Test
    void messageWithMatchingReceiverIsPublishedForCurrentAccount() {
        SyncMessageHandler handler = new SyncMessageHandler();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        XianyuAccount account = new XianyuAccount();
        account.setId(1L);
        account.setUnb("1986587151");
        when(accountMapper.selectById(1L)).thenReturn(account);

        ReflectionTestUtils.setField(handler, "eventPublisher", publisher);
        ReflectionTestUtils.setField(handler, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(handler, "readStateService", mock(ConversationReadStateService.class));

        ReflectionTestUtils.invokeMethod(
                handler,
                "parseAndPublishEvent",
                "1",
                messageJson("1986587151", "4046662982"),
                "/s/para"
        );

        verify(publisher).publishEvent(any(ChatMessageReceivedEvent.class));
    }

    @Test
    void messageWithoutReceiverButGoodsOwnedByOtherAccountIsNotPublished() {
        SyncMessageHandler handler = new SyncMessageHandler();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("999999");
        when(accountMapper.selectById(2L)).thenReturn(account);

        XianyuGoodsInfo otherGoods = new XianyuGoodsInfo();
        otherGoods.setXyGoodId("821224418381");
        otherGoods.setXianyuAccountId(1L);
        when(goodsInfoService.getByXyGoodIdAndAccountId(2L, "821224418381")).thenReturn(null);
        when(goodsInfoService.getByXyGoodId("821224418381")).thenReturn(otherGoods);

        ReflectionTestUtils.setField(handler, "eventPublisher", publisher);
        ReflectionTestUtils.setField(handler, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(handler, "goodsInfoService", goodsInfoService);
        ReflectionTestUtils.setField(handler, "readStateService", mock(ConversationReadStateService.class));

        ReflectionTestUtils.invokeMethod(
                handler,
                "parseAndPublishEvent",
                "2",
                messageJsonWithoutReceiver("4046662982"),
                "/s/para"
        );

        verify(publisher, never()).publishEvent(any(ChatMessageReceivedEvent.class));
    }

    @Test
    void messageWithoutReceiverButGoodsOwnedByCurrentAccountIsPublished() {
        SyncMessageHandler handler = new SyncMessageHandler();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        GoodsInfoService goodsInfoService = mock(GoodsInfoService.class);

        XianyuGoodsInfo currentGoods = new XianyuGoodsInfo();
        currentGoods.setXyGoodId("821224418381");
        currentGoods.setXianyuAccountId(2L);
        when(goodsInfoService.getByXyGoodIdAndAccountId(2L, "821224418381")).thenReturn(currentGoods);

        ReflectionTestUtils.setField(handler, "eventPublisher", publisher);
        ReflectionTestUtils.setField(handler, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(handler, "goodsInfoService", goodsInfoService);
        ReflectionTestUtils.setField(handler, "readStateService", mock(ConversationReadStateService.class));

        ReflectionTestUtils.invokeMethod(
                handler,
                "parseAndPublishEvent",
                "2",
                messageJsonWithoutReceiver("4046662982"),
                "/s/para"
        );

        verify(publisher).publishEvent(any(ChatMessageReceivedEvent.class));
    }

    @Test
    void messageWithoutReceiverAndGoodsIsPublishedForCurrentWebSocketAccount() {
        SyncMessageHandler handler = new SyncMessageHandler();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);

        XianyuAccount account = new XianyuAccount();
        account.setId(1L);
        account.setUnb("1986587151");
        when(accountMapper.selectById(1L)).thenReturn(account);

        ReflectionTestUtils.setField(handler, "eventPublisher", publisher);
        ReflectionTestUtils.setField(handler, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(handler, "readStateService", mock(ConversationReadStateService.class));

        ReflectionTestUtils.invokeMethod(
                handler,
                "parseAndPublishEvent",
                "1",
                systemNoticeJson("856170265"),
                "/s/para"
        );

        verify(publisher).publishEvent(any(ChatMessageReceivedEvent.class));
    }

    private String messageJson(String receiver, String senderUserId) {
        return "{"
                + "\"1\":{"
                + "\"2\":\"60716895233@goofish\","
                + "\"3\":\"4097096240384.PNM\","
                + "\"5\":1778471495897,"
                + "\"10\":{"
                + "\"receiver\":\"" + receiver + "\","
                + "\"senderUserId\":\"" + senderUserId + "\","
                + "\"reminderTitle\":\"买家\","
                + "\"reminderContent\":\"测试消息\","
                + "\"reminderUrl\":\"fleamarket://message_chat?itemId=821224418381\""
                + "}"
                + "}"
                + "}";
    }

    private String messageJsonWithoutReceiver(String senderUserId) {
        return "{"
                + "\"1\":{"
                + "\"2\":\"60716895233@goofish\","
                + "\"3\":\"4097096240385.PNM\","
                + "\"5\":1778471495897,"
                + "\"10\":{"
                + "\"senderUserId\":\"" + senderUserId + "\","
                + "\"reminderTitle\":\"买家\","
                + "\"reminderContent\":\"测试消息\","
                + "\"reminderUrl\":\"fleamarket://message_chat?itemId=821224418381\""
                + "}"
                + "}"
                + "}";
    }

    private String systemNoticeJson(String senderUserId) {
        return "{"
                + "\"1\":{"
                + "\"2\":\"43284150525@goofish\","
                + "\"3\":\"4107475318896.PNM\","
                + "\"5\":1778547853753,"
                + "\"6\":{\"3\":{\"5\":\"{\\\"contentType\\\":14}\"}},"
                + "\"10\":{"
                + "\"senderUserId\":\"" + senderUserId + "\","
                + "\"reminderTitle\":\"二手数码严选店\","
                + "\"reminderContent\":\"队友喊你来打气\","
                + "\"reminderUrl\":\"fleamarket://custom_chat?sid=43284150525\""
                + "}"
                + "}"
                + "}";
    }
}
