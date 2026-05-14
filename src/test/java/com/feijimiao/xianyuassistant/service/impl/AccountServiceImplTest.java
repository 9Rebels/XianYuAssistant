package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.event.account.AccountRemovedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.AccountDataCleanupService;
import com.feijimiao.xianyuassistant.service.AccountIdentityGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceImplTest {

    @Test
    void updateAccountCookieRejectsCrossAccountIdentityBeforeWriting() {
        AccountServiceImpl service = new AccountServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        AccountIdentityGuard identityGuard = mock(AccountIdentityGuard.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "accountIdentityGuard", identityGuard);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("222222");
        when(accountMapper.selectById(2L)).thenReturn(account);
        when(identityGuard.canUseUnb(2L, "111111")).thenReturn(false);

        boolean updated = service.updateAccountCookie(2L, "111111", "unb=111111; _m_h5_tk=token_123");

        assertFalse(updated);
        verify(accountMapper, never()).updateById(any(XianyuAccount.class));
        verify(cookieMapper, never()).updateById(any(XianyuCookie.class));
    }

    @Test
    void deleteAccountAndRelatedDataPublishesEventBeforeCleanup() {
        AccountServiceImpl service = new AccountServiceImpl();
        AccountDataCleanupService cleanupService = mock(AccountDataCleanupService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "accountDataCleanupService", cleanupService);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        boolean deleted = service.deleteAccountAndRelatedData(4L);

        assertTrue(deleted);
        // 必须先发布事件让监听器断开连接/释放资源，再删 DB 数据，否则 DB 删了但内存状态可能残留导致幽灵重连
        InOrder order = inOrder(eventPublisher, cleanupService);
        ArgumentCaptor<AccountRemovedEvent> eventCaptor = ArgumentCaptor.forClass(AccountRemovedEvent.class);
        order.verify(eventPublisher).publishEvent(eventCaptor.capture());
        order.verify(cleanupService).deleteAccountAndRelatedData(4L);
        assertEquals(4L, eventCaptor.getValue().getAccountId());
    }
}
