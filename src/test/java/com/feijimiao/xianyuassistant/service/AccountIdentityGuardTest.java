package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountIdentityGuardTest {

    @Test
    void rejectsCookieWhoseUnbDiffersFromCurrentAccount() {
        AccountIdentityGuard guard = new AccountIdentityGuard();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(guard, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("222222");
        when(accountMapper.selectById(2L)).thenReturn(account);

        assertFalse(guard.canUseCookie(2L, "unb=111111; _m_h5_tk=token_123"));
        assertThrows(IllegalStateException.class,
                () -> guard.assertCookieBelongsToAccount(2L, "unb=111111; _m_h5_tk=token_123"));
    }

    @Test
    void rejectsCookieWhoseUnbIsAlreadyOwnedByAnotherAccount() {
        AccountIdentityGuard guard = new AccountIdentityGuard();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(guard, "accountMapper", accountMapper);

        XianyuAccount current = new XianyuAccount();
        current.setId(2L);
        current.setUnb(null);
        XianyuAccount owner = new XianyuAccount();
        owner.setId(1L);
        owner.setUnb("111111");

        when(accountMapper.selectById(2L)).thenReturn(current);
        when(accountMapper.selectList(any())).thenReturn(List.of(owner));

        assertFalse(guard.canUseCookie(2L, "unb=111111; _m_h5_tk=token_123"));
    }

    @Test
    void allowsCookieWhoseUnbBelongsOnlyToCurrentAccount() {
        AccountIdentityGuard guard = new AccountIdentityGuard();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(guard, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(2L);
        account.setUnb("222222");
        when(accountMapper.selectById(2L)).thenReturn(account);
        when(accountMapper.selectList(any())).thenReturn(List.of(account));

        assertTrue(guard.canUseCookie(2L, "unb=222222; _m_h5_tk=token_123"));
    }
}
