package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Protects local account rows from being overwritten by cookies from another Xianyu account.
 */
@Slf4j
@Component
public class AccountIdentityGuard {

    @Autowired
    private XianyuAccountMapper accountMapper;

    public boolean canUseUnb(Long accountId, String unb) {
        try {
            assertUnbBelongsToAccount(accountId, unb);
            return true;
        } catch (IllegalStateException e) {
            log.warn("账号身份校验失败: accountId={}, unb={}, reason={}", accountId, mask(unb), e.getMessage());
            return false;
        }
    }

    public boolean canUseCookie(Long accountId, String cookieText) {
        return canUseUnb(accountId, extractCookieValue(cookieText, "unb"));
    }

    public void assertCookieBelongsToAccount(Long accountId, String cookieText) {
        assertUnbBelongsToAccount(accountId, extractCookieValue(cookieText, "unb"));
    }

    public void assertUnbBelongsToAccount(Long accountId, String unb) {
        if (accountId == null) {
            throw new IllegalStateException("账号ID不能为空");
        }
        String candidateUnb = normalizeUserId(unb);
        if (!hasText(candidateUnb)) {
            throw new IllegalStateException("Cookie缺少unb，无法确认账号身份");
        }

        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalStateException("账号不存在");
        }

        String currentUnb = normalizeUserId(account.getUnb());
        if (hasText(currentUnb) && !currentUnb.equals(candidateUnb)) {
            throw new IllegalStateException("Cookie身份与当前账号不一致");
        }

        List<XianyuAccount> owners = accountMapper.selectList(
                new LambdaQueryWrapper<XianyuAccount>().eq(XianyuAccount::getUnb, candidateUnb));
        boolean ownedByOther = owners != null && owners.stream()
                .anyMatch(owner -> owner.getId() != null && !owner.getId().equals(accountId));
        if (ownedByOther) {
            throw new IllegalStateException("Cookie身份已属于其他账号，拒绝跨账号写回");
        }
    }

    public String extractCookieValue(String cookieText, String name) {
        if (!hasText(cookieText) || !hasText(name)) {
            return null;
        }
        String prefix = name + "=";
        String[] cookieParts = cookieText.split(";\\s*");
        for (String part : cookieParts) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return null;
    }

    private String normalizeUserId(String value) {
        String text = value == null ? "" : value.trim();
        int atIndex = text.indexOf('@');
        return atIndex > 0 ? text.substring(0, atIndex) : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mask(String value) {
        if (!hasText(value)) {
            return "";
        }
        String text = normalizeUserId(value);
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
    }
}
