package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.MsgContextReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.OnlineConversationDTO;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.entity.XianyuConversationState;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuChatMessageMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.service.ConversationReadStateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceImplTest {

    @Test
    void contextMessagesAreScopedByAccountIdAndSid() {
        ChatMessageServiceImpl service = new ChatMessageServiceImpl();
        XianyuChatMessageMapper chatMessageMapper = mock(XianyuChatMessageMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("999999");
        when(accountMapper.selectById(2L)).thenReturn(account);

        XianyuChatMessage message = new XianyuChatMessage();
        message.setId(8L);
        message.setXianyuAccountId(2L);
        message.setSId("same-sid@goofish");
        message.setPnmId("pnm-2");
        message.setSenderUserId("999999");
        message.setMsgContent("account 2 message");
        message.setMessageTime(1715000000000L);

        when(chatMessageMapper.findRecentByAccountIdAndSId(2L, "same-sid@goofish", 50, 0))
                .thenReturn(List.of(message));

        MsgContextReqDTO req = new MsgContextReqDTO();
        req.setXianyuAccountId(2L);
        req.setSid("same-sid@goofish");
        req.setLimit(50);
        req.setOffset(0);

        ResultObject<?> result = service.getContextMessages(req);

        assertEquals(200, result.getCode());
        assertEquals(1, ((List<?>) result.getData()).size());
        verify(chatMessageMapper).findRecentByAccountIdAndSId(
                eq(2L),
                eq("same-sid@goofish"),
                eq(50),
                eq(0)
        );
    }

    @Test
    void contextMessagesRequireAccountId() {
        ChatMessageServiceImpl service = new ChatMessageServiceImpl();

        MsgContextReqDTO req = new MsgContextReqDTO();
        req.setSid("same-sid@goofish");

        ResultObject<?> result = service.getContextMessages(req);

        assertEquals(404, result.getCode());
    }

    @Test
    void contextMessagesSkipRowsWhoseReceiverDoesNotMatchAccount() {
        ChatMessageServiceImpl service = new ChatMessageServiceImpl();
        XianyuChatMessageMapper chatMessageMapper = mock(XianyuChatMessageMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("999999");
        when(accountMapper.selectById(2L)).thenReturn(account);

        XianyuChatMessage dirtyMessage = new XianyuChatMessage();
        dirtyMessage.setId(9L);
        dirtyMessage.setXianyuAccountId(2L);
        dirtyMessage.setSId("same-sid@goofish");
        dirtyMessage.setPnmId("pnm-dirty");
        dirtyMessage.setSenderUserId("4046662982");
        dirtyMessage.setMsgContent("wrong account message");
        dirtyMessage.setCompleteMsg(messageJson("1986587151"));
        dirtyMessage.setMessageTime(1715000000000L);

        when(chatMessageMapper.findRecentByAccountIdAndSId(2L, "same-sid@goofish", 50, 0))
                .thenReturn(List.of(dirtyMessage));

        MsgContextReqDTO req = new MsgContextReqDTO();
        req.setXianyuAccountId(2L);
        req.setSid("same-sid@goofish");
        req.setLimit(50);
        req.setOffset(0);

        ResultObject<?> result = service.getContextMessages(req);

        assertEquals(200, result.getCode());
        assertEquals(0, ((List<?>) result.getData()).size());
    }

    @Test
    void contextMessagesSkipRowsWhoseGoodsBelongsToAnotherAccount() {
        ChatMessageServiceImpl service = new ChatMessageServiceImpl();
        XianyuChatMessageMapper chatMessageMapper = mock(XianyuChatMessageMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuGoodsInfoMapper goodsInfoMapper = mock(XianyuGoodsInfoMapper.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "goodsInfoMapper", goodsInfoMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("999999");
        when(accountMapper.selectById(2L)).thenReturn(account);

        XianyuGoodsInfo otherGoods = new XianyuGoodsInfo();
        otherGoods.setXyGoodId("821224418381");
        otherGoods.setXianyuAccountId(1L);
        when(goodsInfoMapper.selectList(any())).thenReturn(List.of(otherGoods));

        XianyuChatMessage dirtyMessage = new XianyuChatMessage();
        dirtyMessage.setId(10L);
        dirtyMessage.setXianyuAccountId(2L);
        dirtyMessage.setSId("same-sid@goofish");
        dirtyMessage.setPnmId("pnm-dirty-goods");
        dirtyMessage.setSenderUserId("4046662982");
        dirtyMessage.setXyGoodsId("821224418381");
        dirtyMessage.setMsgContent("wrong account goods message");
        dirtyMessage.setCompleteMsg("{\"1\":{\"10\":{}}}");
        dirtyMessage.setMessageTime(1715000000000L);

        when(chatMessageMapper.findRecentByAccountIdAndSId(2L, "same-sid@goofish", 50, 0))
                .thenReturn(List.of(dirtyMessage));

        MsgContextReqDTO req = new MsgContextReqDTO();
        req.setXianyuAccountId(2L);
        req.setSid("same-sid@goofish");
        req.setLimit(50);
        req.setOffset(0);

        ResultObject<?> result = service.getContextMessages(req);

        assertEquals(200, result.getCode());
        assertEquals(0, ((List<?>) result.getData()).size());
    }

    @Test
    void contextMessagesKeepRowsWithoutReceiverAndGoods() {
        ChatMessageServiceImpl service = new ChatMessageServiceImpl();
        XianyuChatMessageMapper chatMessageMapper = mock(XianyuChatMessageMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(1L);
        account.setUnb("1986587151");
        when(accountMapper.selectById(1L)).thenReturn(account);

        XianyuChatMessage noticeMessage = new XianyuChatMessage();
        noticeMessage.setId(11L);
        noticeMessage.setXianyuAccountId(1L);
        noticeMessage.setSId("43284150525@goofish");
        noticeMessage.setPnmId("4107475318896.PNM");
        noticeMessage.setSenderUserId("856170265");
        noticeMessage.setMsgContent("队友喊你来打气");
        noticeMessage.setCompleteMsg("{\"1\":{\"10\":{\"senderUserId\":\"856170265\"}}}");
        noticeMessage.setMessageTime(1778547853753L);

        when(chatMessageMapper.findRecentByAccountIdAndSId(1L, "43284150525@goofish", 50, 0))
                .thenReturn(List.of(noticeMessage));

        MsgContextReqDTO req = new MsgContextReqDTO();
        req.setXianyuAccountId(1L);
        req.setSid("43284150525@goofish");
        req.setLimit(50);
        req.setOffset(0);

        ResultObject<?> result = service.getContextMessages(req);

        assertEquals(200, result.getCode());
        assertEquals(1, ((List<?>) result.getData()).size());
    }

    @Test
    void onlineConversationsShowUnreadWhenLatestOutgoingIsAfterReadReceipt() {
        ChatMessageServiceImpl service = new ChatMessageServiceImpl();
        XianyuChatMessageMapper chatMessageMapper = mock(XianyuChatMessageMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ConversationReadStateService readStateService = mock(ConversationReadStateService.class);
        ReflectionTestUtils.setField(service, "chatMessageMapper", chatMessageMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "readStateService", readStateService);

        XianyuAccount account = new XianyuAccount();
        account.setId(4L);
        account.setUnb("seller-1");
        when(accountMapper.selectById(4L)).thenReturn(account);

        XianyuChatMessage latestOutgoing = new XianyuChatMessage();
        latestOutgoing.setId(21L);
        latestOutgoing.setXianyuAccountId(4L);
        latestOutgoing.setSId("buyer@goofish");
        latestOutgoing.setPnmId("outgoing.PNM");
        latestOutgoing.setSenderUserId("seller-1");
        latestOutgoing.setSenderUserName("seller");
        latestOutgoing.setMsgContent("自己下单的 能拍就有的");
        latestOutgoing.setMessageTime(1778914383000L);

        XianyuChatMessage peerMessage = new XianyuChatMessage();
        peerMessage.setId(20L);
        peerMessage.setXianyuAccountId(4L);
        peerMessage.setSId("buyer@goofish");
        peerMessage.setPnmId("peer.PNM");
        peerMessage.setSenderUserId("buyer");
        peerMessage.setSenderUserName("买家");
        peerMessage.setMsgContent("你好");
        peerMessage.setMessageTime(1778914020000L);

        XianyuConversationState state = new XianyuConversationState();
        state.setSId("buyer@goofish");
        state.setReadStatus(1);
        state.setReadTimestamp(1778914020000L);

        when(chatMessageMapper.findRecentForConversations(4L, 160))
                .thenReturn(List.of(latestOutgoing, peerMessage));
        when(readStateService.findStates(eq(4L), any()))
                .thenReturn(Map.of("buyer@goofish", state));

        @SuppressWarnings("unchecked")
        List<OnlineConversationDTO> conversations =
                (List<OnlineConversationDTO>) service.getOnlineConversations(4L, 1).getData();

        assertEquals(1, conversations.size());
        assertEquals(0, conversations.get(0).getReadStatus());
        assertEquals("未读", conversations.get(0).getReadStatusText());
    }

    private String messageJson(String receiver) {
        return "{\"1\":{\"10\":{\"receiver\":\"" + receiver + "\"}}}";
    }
}
