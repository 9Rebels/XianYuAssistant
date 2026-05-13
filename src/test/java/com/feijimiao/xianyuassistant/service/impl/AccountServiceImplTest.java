package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.AccountDataCleanupService;
import com.feijimiao.xianyuassistant.service.AccountIdentityGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    void deleteAccountAndRelatedDataDelegatesToFullCleanupService() {
        AccountServiceImpl service = new AccountServiceImpl();
        AccountDataCleanupService cleanupService = mock(AccountDataCleanupService.class);
        ReflectionTestUtils.setField(service, "accountDataCleanupService", cleanupService);

        boolean deleted = service.deleteAccountAndRelatedData(4L);

        assertTrue(deleted);
        verify(cleanupService).deleteAccountAndRelatedData(4L);
    }
}
