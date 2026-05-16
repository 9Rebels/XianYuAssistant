package com.feijimiao.xianyuassistant.event.cookie;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class CookieExpiredNotificationListener {

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private NotificationService notificationService;

    @TransactionalEventListener(fallbackExecution = true)
    public void onCookieExpired(CookieExpiredNotificationEvent event) {
        Long accountId = event.accountId();
        try {
            XianyuAccount account = accountMapper.selectById(accountId);
            String accountNote = account != null ? account.getAccountNote() : null;
            notificationService.notifyEvent(
                    NotificationService.EVENT_COOKIE_EXPIRE,
                    "【闲鱼助手】Cookie 已过期",
                    "账号ID：" + accountId
                            + "\n账号备注：" + (accountNote == null || accountNote.isBlank() ? "-" : accountNote)
                            + "\n说明：该账号 Cookie 已确认无法自动续期，请重新登录或刷新 Cookie。");
        } catch (Exception e) {
            log.error("【账号{}】发送Cookie过期通知失败", accountId, e);
        }
    }
}
