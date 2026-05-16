package com.feijimiao.xianyuassistant.event.cookie;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CookieExpiredNotificationListenerTest {

    @Test
    void sendsCookieExpiredNotificationWithAccountNote() {
        CookieExpiredNotificationListener listener = new CookieExpiredNotificationListener();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        ReflectionTestUtils.setField(listener, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(listener, "notificationService", notificationService);

        XianyuAccount account = new XianyuAccount();
        account.setAccountNote("note");
        when(accountMapper.selectById(5L)).thenReturn(account);

        listener.onCookieExpired(new CookieExpiredNotificationEvent(5L));

        verify(notificationService).notifyEvent(
                eq(NotificationService.EVENT_COOKIE_EXPIRE),
                eq("【闲鱼助手】Cookie 已过期"),
                eq("账号ID：5\n账号备注：note\n说明：该账号 Cookie 已确认无法自动续期，请重新登录或刷新 Cookie。"));
    }
}
