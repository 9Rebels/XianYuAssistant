package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.enums.AccountStatus;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.service.AccountStateService;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountStateServiceImpl implements AccountStateService {

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Override
    public boolean markNormal(Long accountId, String reason) {
        return updateStatus(accountId, AccountStatus.NORMAL, reason);
    }

    @Override
    public boolean restoreNormalIfCaptchaRequired(Long accountId, String reason) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            log.warn("【账号{}】账号不存在，无法恢复正常状态", accountId);
            return false;
        }
        if (!isCaptchaRequired(account)) {
            return false;
        }
        return updateStatus(accountId, AccountStatus.NORMAL, reason);
    }

    @Override
    public boolean markCaptchaRequired(Long accountId, String reason) {
        return updateStatus(accountId, AccountStatus.CAPTCHA_REQUIRED, reason);
    }

    @Override
    public boolean isNormal(XianyuAccount account) {
        return account != null && AccountStatus.isNormal(account.getStatus());
    }

    @Override
    public boolean isCaptchaRequired(XianyuAccount account) {
        return account != null && AccountStatus.isCaptchaRequired(account.getStatus());
    }

    private boolean updateStatus(Long accountId, AccountStatus status, String reason) {
        if (accountId == null || status == null) {
            return false;
        }
        try {
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                log.warn("【账号{}】账号不存在，无法更新状态为{}", accountId, status.getDescription());
                return false;
            }
            if (status.getCode().equals(account.getStatus())) {
                return false;
            }
            String now = DateTimeUtils.currentShanghaiTime();
            account.setStatus(status.getCode());
            account.setUpdatedTime(now);
            account.setStateReason(reason);
            account.setStateUpdatedTime(now);
            updateByIdWithStateFieldsFallback(account);
            log.info("【账号{}】账号状态已更新为{}({}), reason={}",
                    accountId, status.getDescription(), status.getCode(), reason);
            return true;
        } catch (Exception e) {
            log.error("【账号{}】账号状态更新失败: {}", accountId, status.getDescription(), e);
            return false;
        }
    }

    private void updateByIdWithStateFieldsFallback(XianyuAccount account) {
        try {
            accountMapper.updateById(account);
        } catch (DataAccessException e) {
            if (!isMissingStateColumn(e)) {
                throw e;
            }
            account.setStateReason(null);
            account.setStateUpdatedTime(null);
            accountMapper.updateById(account);
            log.debug("【账号{}】状态灰度字段未就绪，已降级只写基础状态字段", account.getId());
        }
    }

    private boolean isMissingStateColumn(DataAccessException e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("state_reason") || message.contains("state_updated_time"));
    }
}
