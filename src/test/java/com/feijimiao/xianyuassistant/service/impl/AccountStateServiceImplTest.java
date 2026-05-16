package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.enums.AccountStatus;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountStateServiceImplTest {

    @Test
    void restoreNormalOnlyUpdatesCaptchaRequiredAccounts() {
        AccountStateServiceImpl service = new AccountStateServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(AccountStatus.CAPTCHA_REQUIRED.getCode());
        when(accountMapper.selectById(7L)).thenReturn(account);

        assertTrue(service.restoreNormalIfCaptchaRequired(7L, "ok"));

        verify(accountMapper).updateById(account);
        assertTrue(AccountStatus.isNormal(account.getStatus()));
        assertTrue(account.getStateReason().contains("ok"));
        assertTrue(account.getStateUpdatedTime() != null);
    }

    @Test
    void restoreNormalDoesNothingForAlreadyNormalAccounts() {
        AccountStateServiceImpl service = new AccountStateServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setStatus(AccountStatus.NORMAL.getCode());
        when(accountMapper.selectById(8L)).thenReturn(account);

        assertFalse(service.restoreNormalIfCaptchaRequired(8L, "ok"));

        verify(accountMapper, never()).updateById(any());
    }

    @Test
    void markCaptchaRequiredUpdatesNormalAccounts() {
        AccountStateServiceImpl service = new AccountStateServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(9L);
        account.setStatus(AccountStatus.NORMAL.getCode());
        when(accountMapper.selectById(9L)).thenReturn(account);

        assertTrue(service.markCaptchaRequired(9L, "captcha"));

        verify(accountMapper).updateById(account);
        assertTrue(AccountStatus.isCaptchaRequired(account.getStatus()));
    }

    @Test
    void markNormalReturnsFalseWhenAccountMissing() {
        AccountStateServiceImpl service = new AccountStateServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        when(accountMapper.selectById(10L)).thenReturn(null);

        assertFalse(service.markNormal(10L, "missing"));

        verify(accountMapper, never()).updateById(any());
    }

    @Test
    void updateFallsBackWhenStateColumnsAreNotMigrated() {
        AccountStateServiceImpl service = new AccountStateServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);

        XianyuAccount account = new XianyuAccount();
        account.setId(11L);
        account.setStatus(AccountStatus.CAPTCHA_REQUIRED.getCode());
        when(accountMapper.selectById(11L)).thenReturn(account);
        doThrow(new DataAccessException("no such column: state_reason") {
        })
                .doReturn(1)
                .when(accountMapper).updateById(account);

        assertTrue(service.restoreNormalIfCaptchaRequired(11L, "ok"));

        verify(accountMapper, times(2)).updateById(account);
        assertTrue(account.getStateReason() == null);
        assertTrue(account.getStateUpdatedTime() == null);
    }
}
