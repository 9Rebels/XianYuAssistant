package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.mapper.XianyuConversationStateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConversationReadStateServiceImplTest {

    @Test
    void markReadReceiptNormalizesMicrosecondTimestampToMillis() {
        XianyuConversationStateMapper mapper = mock(XianyuConversationStateMapper.class);
        ConversationReadStateServiceImpl service = new ConversationReadStateServiceImpl(mapper);

        service.markReadReceipt(4L, """
                {"2":"2","1":{"2":"buyer@goofish","3":"msg-1","5":1778914383000000}}
                """);

        ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mapper).upsertReadReceipt(
                eq(4L),
                eq("buyer@goofish"),
                eq("msg-1"),
                timestampCaptor.capture(),
                org.mockito.ArgumentMatchers.anyString()
        );
        assertEquals(1778914383000L, timestampCaptor.getValue());
    }

    @Test
    void markOutgoingUnreadNormalizesMessageTimestampToMillis() {
        XianyuConversationStateMapper mapper = mock(XianyuConversationStateMapper.class);
        ConversationReadStateServiceImpl service = new ConversationReadStateServiceImpl(mapper);

        service.markOutgoingUnread(4L, "buyer@goofish", 1778914383000000L);

        verify(mapper).markOutgoingUnread(4L, "buyer@goofish", 1778914383000L);
    }
}
