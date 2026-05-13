package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.utils.AccountDisplayNameUtils;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

@Component
public class NotificationContentBuilder {

    private final XianyuAccountMapper accountMapper;
    private final AccountDisplayNameUtils accountDisplayNameUtils;

    public NotificationContentBuilder(
            XianyuAccountMapper accountMapper,
            AccountDisplayNameUtils accountDisplayNameUtils
    ) {
        this.accountMapper = accountMapper;
        this.accountDisplayNameUtils = accountDisplayNameUtils;
    }

    public String accountLabel(Long accountId) {
        return accountDisplayNameUtils.getDisplayName(accountId);
    }

    public String accountNote(Long accountId) {
        XianyuAccount account = account(accountId);
        if (account == null || account.getAccountNote() == null || account.getAccountNote().isBlank()) {
            return "-";
        }
        return account.getAccountNote().trim();
    }

    public String accountDisplayName(Long accountId) {
        XianyuAccount account = account(accountId);
        if (account == null || account.getDisplayName() == null || account.getDisplayName().isBlank()) {
            return "-";
        }
        return account.getDisplayName().trim();
    }

    public String accountIntro(Long accountId) {
        return "账号：" + accountLabel(accountId)
                + "\n账号备注：" + accountNote(accountId)
                + "\n闲鱼昵称：" + accountDisplayName(accountId);
    }

    public String eventContent(Long accountId, String reason, String detail, String action) {
        return accountIntro(accountId)
                + "\n原因：" + valueOrDash(reason)
                + "\n详细内容：" + valueOrDash(detail)
                + "\n处理建议：" + valueOrDash(action)
                + "\n时间：" + DateTimeUtils.currentShanghaiTime();
    }

    private XianyuAccount account(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountMapper.selectById(accountId);
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }
}
