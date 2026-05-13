package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.AccountIdentityGuard;
import com.feijimiao.xianyuassistant.service.AccountDataCleanupService;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 账号服务实现类
 */
@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private static final int MANUAL_PROFILE_REFRESH_MINUTES = 60;
    private static final String USER_PAGE_HEAD_API = "mtop.idle.web.user.page.head";
    private static final String USER_PAGE_NAV_API = "mtop.idle.web.user.page.nav";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private XianyuApiRecoveryService xianyuApiRecoveryService;

    @Autowired
    private AccountIdentityGuard accountIdentityGuard;

    @Autowired
    private AccountDataCleanupService accountDataCleanupService;
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTimeString() {
        return DateTimeUtils.currentShanghaiTime();
    }
    
    /**
     * 获取未来时间字符串
     */
    private String getFutureTimeString(int days) {
        return DateTimeUtils.currentShanghaiDateTime().plusDays(days)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    
    /**
     * 从Cookie字符串中提取_m_h5_tk值
     *
     * @param cookie Cookie字符串
     * @return _m_h5_tk值，如果未找到则返回null
     */
    private String extractMH5TkFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) {
            return null;
        }
        
        // 查找_m_h5_tk=后面的值
        String[] cookieParts = cookie.split(";\\s*");
        for (String part : cookieParts) {
            if (part.startsWith("_m_h5_tk=")) {
                return part.substring(9); // "_m_h5_tk=".length() = 9
            }
        }
        
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAccountAndCookie(String accountNote, String unb, String cookieText) {
        return saveAccountAndCookie(accountNote, unb, cookieText, null);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAccountAndCookie(String accountNote, String unb, String cookieText, String mH5Tk) {
        try {
            String normalizedAccountNote = accountNote == null ? "" : accountNote.trim();
            log.info("开始保存账号和Cookie: accountNote={}, unb={}, 包含m_h5_tk={}", 
                    normalizedAccountNote, unb, mH5Tk != null);

            // 1. 检查账号是否已存在（根据UNB）
            LambdaQueryWrapper<XianyuAccount> accountQuery = new LambdaQueryWrapper<>();
            accountQuery.eq(XianyuAccount::getUnb, unb);
            List<XianyuAccount> existingAccounts = accountMapper.selectList(accountQuery);
            if (existingAccounts != null && existingAccounts.size() > 1) {
                throw new IllegalStateException("UNB对应多个本地账号，请先修正重复账号数据");
            }
            XianyuAccount existingAccount = existingAccounts == null || existingAccounts.isEmpty()
                    ? null
                    : existingAccounts.get(0);

            Long accountId;
            if (existingAccount != null) {
                // 账号已存在，更新信息
                accountId = existingAccount.getId();
                if (!normalizedAccountNote.isEmpty()) {
                    existingAccount.setAccountNote(normalizedAccountNote);
                }
                existingAccount.setStatus(1); // 正常状态
                existingAccount.setUpdatedTime(getCurrentTimeString());
                accountMapper.updateById(existingAccount);
                log.info("账号已存在，更新账号信息: accountId={}", accountId);
            } else {
                // 创建新账号
                XianyuAccount account = new XianyuAccount();
                account.setAccountNote(normalizedAccountNote);
                account.setUnb(unb);
                account.setStatus(1);
                account.setCreatedTime(getCurrentTimeString());
                account.setUpdatedTime(getCurrentTimeString());
                accountMapper.insert(account);
                accountId = account.getId();
                log.info("创建新账号成功: accountId={}", accountId);
            }

            // 2. 保存或更新Cookie
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, accountId);
            XianyuCookie existingCookie = cookieMapper.selectOne(cookieQuery);

            if (existingCookie != null) {
                // Cookie已存在，更新
                existingCookie.setCookieText(cookieText);
                existingCookie.setMH5Tk(mH5Tk);
                existingCookie.setCookieStatus(1); // 有效状态
                existingCookie.setExpireTime(getFutureTimeString(30)); // 30天后过期
                existingCookie.setUpdatedTime(getCurrentTimeString());
                cookieMapper.updateById(existingCookie);
                log.info("更新Cookie成功: cookieId={}, m_h5_tk={}", 
                        existingCookie.getId(), mH5Tk != null ? "已保存" : "未提供");
            } else {
                // 创建新Cookie
                XianyuCookie cookie = new XianyuCookie();
                cookie.setXianyuAccountId(accountId);
                cookie.setCookieText(cookieText);
                cookie.setMH5Tk(mH5Tk);
                cookie.setCookieStatus(1);
                cookie.setExpireTime(getFutureTimeString(30));
                cookie.setCreatedTime(getCurrentTimeString());
                cookie.setUpdatedTime(getCurrentTimeString());
                cookieMapper.insert(cookie);
                log.info("创建新Cookie成功: cookieId={}, m_h5_tk={}", 
                        cookie.getId(), mH5Tk != null ? "已保存" : "未提供");
            }

            log.info("保存账号和Cookie完成: accountId={}, accountNote={}", accountId, normalizedAccountNote);
            return accountId;

        } catch (Exception e) {
            log.error("保存账号和Cookie失败: accountNote={}, unb={}", accountNote, unb, e);
            throw new RuntimeException("保存账号和Cookie失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getCookieByAccountId(Long accountId) {
        try {
            log.info("根据账号ID获取Cookie: accountId={}", accountId);

            // 查询最新的有效Cookie
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, accountId)
                    .eq(XianyuCookie::getCookieStatus, 1) // 只查询有效的Cookie
                    .orderByDesc(XianyuCookie::getCreatedTime)
                    .last("LIMIT 1");
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie == null) {
                log.warn("未找到有效Cookie: accountId={}", accountId);
                return null;
            }

            log.info("获取Cookie成功: accountId={}", accountId);
            return cookie.getCookieText();

        } catch (Exception e) {
            log.error("获取Cookie失败: accountId={}", accountId, e);
            return null;
        }
    }

    @Override
    public String getCookieByUnb(String unb) {
        try {
            // 1. 根据UNB查询账号
            LambdaQueryWrapper<XianyuAccount> accountQuery = new LambdaQueryWrapper<>();
            accountQuery.eq(XianyuAccount::getUnb, unb);
            XianyuAccount account = accountMapper.selectOne(accountQuery);

            if (account == null) {
                log.warn("未找到账号: unb={}", unb);
                return null;
            }

            // 2. 查询Cookie
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, account.getId())
                    .eq(XianyuCookie::getCookieStatus, 1) // 只查询有效的Cookie
                    .orderByDesc(XianyuCookie::getCreatedTime)
                    .last("LIMIT 1");
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie == null) {
                log.warn("未找到有效Cookie: accountId={}", account.getId());
                return null;
            }

            log.info("获取Cookie成功: unb={}, accountId={}", unb, account.getId());
            return cookie.getCookieText();

        } catch (Exception e) {
            log.error("获取Cookie失败: unb={}", unb, e);
            return null;
        }
    }

    @Override
    public String getCookieByAccountNote(String accountNote) {
        try {
            // 1. 根据账号备注查询账号
            LambdaQueryWrapper<XianyuAccount> accountQuery = new LambdaQueryWrapper<>();
            accountQuery.eq(XianyuAccount::getAccountNote, accountNote);
            XianyuAccount account = accountMapper.selectOne(accountQuery);

            if (account == null) {
                log.warn("未找到账号: accountNote={}", accountNote);
                return null;
            }

            // 2. 查询Cookie
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, account.getId())
                    .eq(XianyuCookie::getCookieStatus, 1)
                    .orderByDesc(XianyuCookie::getCreatedTime)
                    .last("LIMIT 1");
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie == null) {
                log.warn("未找到有效Cookie: accountId={}", account.getId());
                return null;
            }

            log.info("获取Cookie成功: accountNote={}, accountId={}", accountNote, account.getId());
            return cookie.getCookieText();

        } catch (Exception e) {
            log.error("获取Cookie失败: accountNote={}", accountNote, e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCookie(Long accountId, String cookieText) {
        try {
            log.info("更新Cookie: accountId={}", accountId);
            if (!accountIdentityGuard.canUseCookie(accountId, cookieText)) {
                log.warn("拒绝更新Cookie: accountId={} 的Cookie身份不匹配", accountId);
                return false;
            }

            // 查询现有Cookie
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, accountId);
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie != null) {
                // 更新现有Cookie
                cookie.setCookieText(cookieText);
                cookie.setCookieStatus(1);
                cookie.setExpireTime(getFutureTimeString(30));
                cookie.setUpdatedTime(getCurrentTimeString());
                cookieMapper.updateById(cookie);
            } else {
                // 创建新Cookie
                cookie = new XianyuCookie();
                cookie.setXianyuAccountId(accountId);
                cookie.setCookieText(cookieText);
                cookie.setCookieStatus(1);
                cookie.setExpireTime(getFutureTimeString(30));
                cookie.setCreatedTime(getCurrentTimeString());
                cookie.setUpdatedTime(getCurrentTimeString());
                cookieMapper.insert(cookie);
            }

            log.info("更新Cookie成功: accountId={}", accountId);
            return true;

        } catch (Exception e) {
            log.error("更新Cookie失败: accountId={}", accountId, e);
            return false;
        }
    }
    
    @Override
    public String getMh5tkByAccountId(Long accountId) {
        try {
            log.info("根据账号ID获取m_h5_tk: accountId={}", accountId);

            // 查询Cookie记录
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, accountId)
                    .eq(XianyuCookie::getCookieStatus, 1)
                    .orderByDesc(XianyuCookie::getCreatedTime)
                    .last("LIMIT 1");
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie == null) {
                log.warn("未找到Cookie记录: accountId={}", accountId);
                return null;
            }

            String mH5Tk = cookie.getMH5Tk();
            if (mH5Tk != null && !mH5Tk.isEmpty()) {
                log.info("获取m_h5_tk成功: accountId={}", accountId);
            } else {
                log.warn("m_h5_tk为空: accountId={}", accountId);
            }
            
            return mH5Tk;

        } catch (Exception e) {
            log.error("获取m_h5_tk失败: accountId={}", accountId, e);
            return null;
        }
    }
    
    @Override
    public Long getAccountIdByAccountNote(String accountNote) {
        try {
            log.info("根据账号备注获取账号ID: accountNote={}", accountNote);

            // 查询账号
            LambdaQueryWrapper<XianyuAccount> accountQuery = new LambdaQueryWrapper<>();
            accountQuery.eq(XianyuAccount::getAccountNote, accountNote);
            XianyuAccount account = accountMapper.selectOne(accountQuery);

            if (account == null) {
                log.warn("未找到账号: accountNote={}", accountNote);
                return null;
            }

            log.info("获取账号ID成功: accountNote={}, accountId={}", accountNote, account.getId());
            return account.getId();

        } catch (Exception e) {
            log.error("获取账号ID失败: accountNote={}", accountNote, e);
            return null;
        }
    }
    
    @Override
    public Long getAccountIdByUnb(String unb) {
        try {
            log.info("根据UNB获取账号ID: unb={}", unb);

            // 查询账号
            LambdaQueryWrapper<XianyuAccount> accountQuery = new LambdaQueryWrapper<>();
            accountQuery.eq(XianyuAccount::getUnb, unb);
            XianyuAccount account = accountMapper.selectOne(accountQuery);

            if (account == null) {
                log.warn("未找到账号: unb={}", unb);
                return null;
            }

            log.info("获取账号ID成功: unb={}, accountId={}", unb, account.getId());
            return account.getId();

        } catch (Exception e) {
            log.error("获取账号ID失败: unb={}", unb, e);
            return null;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCookieStatus(Long accountId, Integer cookieStatus) {
        return updateCookieStatus(accountId, cookieStatus, false);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCookieStatus(Long accountId, Integer cookieStatus, boolean sendNotify) {
        try {
            log.info("更新Cookie状态: accountId={}, cookieStatus={}, sendNotify={}", accountId, cookieStatus, sendNotify);

            // 查询Cookie记录
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, accountId);
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie == null) {
                log.warn("未找到Cookie记录: accountId={}", accountId);
                return false;
            }

            Integer oldStatus = cookie.getCookieStatus();
            
            // 更新Cookie状态
            cookie.setCookieStatus(cookieStatus);
            cookie.setUpdatedTime(getCurrentTimeString());
            cookieMapper.updateById(cookie);

            log.info("更新Cookie状态成功: accountId={}, cookieStatus={}", accountId, cookieStatus);
            
            // 只有在明确指定发送通知时才发送邮件（即确认无法自动续期后）
            if (sendNotify && Objects.equals(cookieStatus, 2) && !Objects.equals(oldStatus, 2)) {
                try {
                    XianyuAccount account = accountMapper.selectById(accountId);
                    String accountNote = account != null ? account.getAccountNote() : null;
                    log.info("【账号{}】Cookie已确认无法自动续期，触发Cookie过期通知流程", accountId);
                    notificationService.notifyEvent(
                            NotificationService.EVENT_COOKIE_EXPIRE,
                            "【闲鱼助手】Cookie 已过期",
                            "账号ID：" + accountId
                                    + "\n账号备注：" + (accountNote == null || accountNote.isBlank() ? "-" : accountNote)
                                    + "\n说明：该账号 Cookie 已确认无法自动续期，请重新登录或刷新 Cookie。"
                    );
                } catch (Exception e) {
                    log.error("【账号{}】发送Cookie过期通知失败", accountId, e);
                }
            } else if (Objects.equals(cookieStatus, 2) && !Objects.equals(oldStatus, 2)) {
                log.info("【账号{}】Cookie被标记为过期，但未指定发送通知（可能系统将尝试自动续期）", accountId);
            }
            
            return true;

        } catch (Exception e) {
            log.error("更新Cookie状态失败: accountId={}, cookieStatus={}", accountId, cookieStatus, e);
            return false;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAccountAndRelatedData(Long accountId) {
        try {
            log.info("开始删除账号及其所有关联数据: accountId={}", accountId);
            accountDataCleanupService.deleteAccountAndRelatedData(accountId);
            log.info("账号及其所有关联数据删除成功: accountId={}", accountId);
            return true;
        } catch (Exception e) {
            log.error("删除账号及其关联数据失败: accountId={}", accountId, e);
            throw new RuntimeException("删除账号失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAccountCookie(Long accountId, String unb, String cookieText) {
        try {
            log.info("更新账号Cookie: accountId={}, unb={}", accountId, unb);

            // 1. 更新账号的UNB
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                log.warn("账号不存在: accountId={}", accountId);
                return false;
            }
            if (!accountIdentityGuard.canUseUnb(accountId, unb)) {
                log.warn("拒绝跨账号更新Cookie: accountId={}, currentUnb={}, newUnb={}",
                        accountId, account.getUnb(), unb);
                return false;
            }
            
            account.setUnb(unb);
            account.setUpdatedTime(getCurrentTimeString());
            accountMapper.updateById(account);
            log.info("更新账号UNB成功: accountId={}, unb={}", accountId, unb);

            // 2. 提取_m_h5_tk
            String mH5Tk = extractMH5TkFromCookie(cookieText);

            // 3. 查询现有Cookie
            LambdaQueryWrapper<XianyuCookie> cookieQuery = new LambdaQueryWrapper<>();
            cookieQuery.eq(XianyuCookie::getXianyuAccountId, accountId);
            XianyuCookie cookie = cookieMapper.selectOne(cookieQuery);

            if (cookie != null) {
                // 更新现有Cookie
                cookie.setCookieText(cookieText);
                cookie.setMH5Tk(mH5Tk);
                cookie.setCookieStatus(1);
                cookie.setExpireTime(getFutureTimeString(30));
                cookie.setUpdatedTime(getCurrentTimeString());
                cookieMapper.updateById(cookie);
                log.info("更新Cookie成功: accountId={}", accountId);
            } else {
                // 创建新Cookie
                cookie = new XianyuCookie();
                cookie.setXianyuAccountId(accountId);
                cookie.setCookieText(cookieText);
                cookie.setMH5Tk(mH5Tk);
                cookie.setCookieStatus(1);
                cookie.setExpireTime(getFutureTimeString(30));
                cookie.setCreatedTime(getCurrentTimeString());
                cookie.setUpdatedTime(getCurrentTimeString());
                cookieMapper.insert(cookie);
                log.info("创建Cookie成功: accountId={}", accountId);
            }

            return true;

        } catch (Exception e) {
            log.error("更新账号Cookie失败: accountId={}, unb={}", accountId, unb, e);
            return false;
        }
    }
    
    @Override
    public String getOrGenerateDeviceId(Long accountId, String unb) {
        try {
            log.info("获取或生成设备ID: accountId={}, unb={}", accountId, unb);
            
            // 1. 查询账号
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                log.warn("账号不存在: accountId={}", accountId);
                return null;
            }
            
            // 2. 检查是否已有设备ID
            String existingDeviceId = account.getDeviceId();
            if (existingDeviceId != null && !existingDeviceId.isEmpty()) {
                log.info("使用已有设备ID: accountId={}, deviceId={}", accountId, existingDeviceId);
                return existingDeviceId;
            }
            
            // 3. 生成新的设备ID
            String newDeviceId = com.feijimiao.xianyuassistant.utils.XianyuDeviceUtils.generateDeviceId(unb);
            log.info("生成新设备ID: accountId={}, unb={}, deviceId={}", accountId, unb, newDeviceId);
            
            // 4. 保存到数据库
            account.setDeviceId(newDeviceId);
            account.setUpdatedTime(getCurrentTimeString());
            accountMapper.updateById(account);
            log.info("设备ID已保存到数据库: accountId={}, deviceId={}", accountId, newDeviceId);
            
            return newDeviceId;
            
        } catch (Exception e) {
            log.error("获取或生成设备ID失败: accountId={}, unb={}", accountId, unb, e);
            return null;
        }
    }
    
    @Override
    public boolean updateDeviceId(Long accountId, String deviceId) {
        try {
            log.info("更新设备ID: accountId={}, deviceId={}", accountId, deviceId);
            
            // 查询账号
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                log.warn("账号不存在: accountId={}", accountId);
                return false;
            }
            
            // 更新设备ID
            account.setDeviceId(deviceId);
            account.setUpdatedTime(getCurrentTimeString());
            accountMapper.updateById(account);
            
            log.info("设备ID更新成功: accountId={}, deviceId={}", accountId, deviceId);
            return true;
            
        } catch (Exception e) {
            log.error("更新设备ID失败: accountId={}, deviceId={}", accountId, deviceId, e);
            return false;
        }
    }
    
    @Override
    public String getXianyuUserId(Long accountId) {
        try {
            // 查询账号
            XianyuAccount account = accountMapper.selectById(accountId);
            if (account == null) {
                log.warn("账号不存在: accountId={}", accountId);
                return null;
            }
            
            // UNB就是闲鱼用户ID
            String unb = account.getUnb();
            log.debug("获取闲鱼用户ID: accountId={}, unb={}", accountId, unb);
            return unb;
            
        } catch (Exception e) {
            log.error("获取闲鱼用户ID失败: accountId={}", accountId, e);
            return null;
        }
    }

    @Override
    public void refreshAccountProfilesIfNeeded(List<XianyuAccount> accounts) {
        log.debug("已禁用账号资料自动刷新，避免页面加载触发闲鱼资料接口");
    }

    @Override
    public XianyuAccount refreshAccountProfileManually(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("账号ID不能为空");
        }

        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }

        assertManualProfileRefreshAllowed(account);
        recordProfileRefreshAttempt(account);

        try {
            refreshAccountProfile(account);
            XianyuAccount refreshed = accountMapper.selectById(accountId);
            return refreshed != null ? refreshed : account;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("刷新账号资料失败: " + e.getMessage(), e);
        }
    }

    private void assertManualProfileRefreshAllowed(XianyuAccount account) {
        String attemptTime = account.getProfileRefreshAttemptTime();
        if (attemptTime == null || attemptTime.isBlank()) {
            return;
        }
        try {
            LocalDateTime lastAttempt = LocalDateTime.parse(attemptTime,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            long elapsedMinutes = ChronoUnit.MINUTES.between(lastAttempt, DateTimeUtils.currentShanghaiDateTime());
            if (elapsedMinutes < MANUAL_PROFILE_REFRESH_MINUTES) {
                long remainingMinutes = MANUAL_PROFILE_REFRESH_MINUTES - elapsedMinutes;
                throw new IllegalStateException("账号资料1小时内只能刷新一次，请约"
                        + remainingMinutes + "分钟后再试");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.debug("账号资料刷新尝试时间解析失败，允许重新刷新: accountId={}, attemptTime={}",
                    account.getId(), attemptTime);
        }
    }

    private void recordProfileRefreshAttempt(XianyuAccount account) {
        account.setProfileRefreshAttemptTime(DateTimeUtils.currentShanghaiTime());
        account.setUpdatedTime(DateTimeUtils.currentShanghaiTime());
        accountMapper.updateById(account);
    }

    @SuppressWarnings("unchecked")
    private void refreshAccountProfile(XianyuAccount account) throws Exception {
        String cookieStr = getCookieByAccountId(account.getId());
        if (cookieStr == null || cookieStr.isBlank()) {
            return;
        }
        accountIdentityGuard.assertCookieBelongsToAccount(account.getId(), cookieStr);

        Map<String, Object> dataMap = new HashMap<>();
        if (account.getUnb() != null && !account.getUnb().isBlank()) {
            dataMap.put("userId", account.getUnb());
        }

        XianyuApiRecoveryResult headResult = callProfileApi(
                account.getId(), "账号资料刷新", USER_PAGE_HEAD_API, dataMap, cookieStr);
        if (!headResult.isSuccess()) {
            throw new IllegalStateException(buildProfileRefreshFailureMessage(headResult));
        }
        String headResponse = headResult.getResponse();
        if (headResult.getCookieText() != null && !headResult.getCookieText().isBlank()) {
            accountIdentityGuard.assertCookieBelongsToAccount(account.getId(), headResult.getCookieText());
        }

        Map<String, Object> headMap = objectMapper.readValue(headResponse, Map.class);

        Map<String, Object> headData = (Map<String, Object>) headMap.get("data");
        Map<String, Object> module = getMap(headData, "module");
        Map<String, Object> base = getMap(module, "base");
        Map<String, Object> social = getMap(module, "social");
        Map<String, Object> shop = getMap(module, "shop");
        Map<String, Object> tabs = getMap(module, "tabs");
        Map<String, Object> itemTab = getMap(tabs, "item");

        account.setDisplayName(getString(base, "displayName", account.getDisplayName()));
        account.setIpLocation(getString(base, "ipLocation", account.getIpLocation()));
        account.setIntroduction(getString(base, "introduction", account.getIntroduction()));
        account.setAvatar(resolveAvatar(base, account.getAvatar()));
        account.setFollowers(getString(social, "followers", account.getFollowers()));
        account.setFollowing(getString(social, "following", account.getFollowing()));
        account.setSellerLevel(getString(shop, "level", account.getSellerLevel()));
        account.setFishShopLevel(getString(shop, "level", null));
        account.setFishShopScore(getInteger(shop, "score", null));
        account.setFishShopUser(resolveFishShopUser(shop));
        account.setPraiseRatio(getString(shop, "praiseRatio", account.getPraiseRatio()));
        account.setReviewNum(getInteger(shop, "reviewNum", account.getReviewNum()));
        account.setSoldCount(getInteger(itemTab, "number", account.getSoldCount()));

        fillNavProfile(account, headResult.getCookieText() != null ? headResult.getCookieText() : cookieStr);

        account.setProfileUpdatedTime(DateTimeUtils.currentShanghaiTime());
        account.setUpdatedTime(DateTimeUtils.currentShanghaiTime());
        accountMapper.updateById(account);
        log.info("刷新账号个人资料成功: accountId={}, displayName={}", account.getId(), account.getDisplayName());
    }

    private String buildProfileRefreshFailureMessage(XianyuApiRecoveryResult apiResult) {
        if (apiResult != null && apiResult.getRecoveryResult() != null
                && apiResult.getRecoveryResult().isNeedManual()) {
            return "账号资料刷新失败：已尝试自动刷新，仍需完成滑块验证后重试";
        }
        String retText = apiResult != null && apiResult.getErrorMessage() != null
                ? apiResult.getErrorMessage()
                : "";
        if (retText.contains("FAIL_SYS_TOKEN_EXOIRED") || retText.contains("FAIL_SYS_TOKEN_EXPIRED")) {
            return "账号资料刷新失败：已尝试自动刷新和验证，仍失败，请人工更新Cookie";
        }
        if (retText.isBlank()) {
            return "账号资料刷新失败：闲鱼接口返回异常";
        }
        return "账号资料刷新失败：" + retText;
    }

    @SuppressWarnings("unchecked")
    private void fillNavProfile(XianyuAccount account, String cookieStr) {
        try {
            XianyuApiRecoveryResult navResult = callProfileApi(
                    account.getId(), "账号导航资料补充", USER_PAGE_NAV_API, new HashMap<>(), cookieStr);
            if (!navResult.isSuccess()) {
                return;
            }
            String navResponse = navResult.getResponse();

            Map<String, Object> navMap = objectMapper.readValue(navResponse, Map.class);
            Map<String, Object> navData = (Map<String, Object>) navMap.get("data");
            Map<String, Object> module = getMap(navData, "module");
            Map<String, Object> base = getMap(module, "base");

            account.setPurchaseCount(getInteger(base, "purchaseCount", account.getPurchaseCount()));
            account.setSoldCount(getInteger(base, "soldCount", account.getSoldCount()));
            account.setFollowers(getString(base, "followers", account.getFollowers()));
            account.setFollowing(getString(base, "following", account.getFollowing()));
            account.setDisplayName(getString(base, "displayName", account.getDisplayName()));
            account.setAvatar(getString(base, "avatar", account.getAvatar()));
        } catch (Exception e) {
            log.debug("补充账号导航资料失败: accountId={}, error={}", account.getId(), e.getMessage());
        }
    }

    private XianyuApiRecoveryResult callProfileApi(Long accountId, String operationName, String apiName,
                                                   Map<String, Object> dataMap, String cookieStr) {
        XianyuApiRecoveryRequest request = new XianyuApiRecoveryRequest();
        request.setAccountId(accountId);
        request.setOperationName(operationName);
        request.setApiName(apiName);
        request.setDataMap(dataMap);
        request.setCookie(cookieStr);
        request.setSpmCnt("a21ybx.home.0.0");
        return xianyuApiRecoveryService.callApi(request);
    }

    private Boolean resolveFishShopUser(Map<String, Object> shop) {
        String level = getString(shop, "level", null);
        Object superShow = shop == null ? null : shop.get("superShow");
        if (level == null || level.isBlank()) {
            return Boolean.FALSE;
        }
        if (superShow instanceof Boolean) {
            return (Boolean) superShow;
        }
        return Boolean.TRUE;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return new HashMap<>();
        }
        Object value = source.get(key);
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }

    private String resolveAvatar(Map<String, Object> base, String fallback) {
        Object avatar = base != null ? base.get("avatar") : null;
        if (avatar instanceof String avatarString && !avatarString.isBlank()) {
            return avatarString;
        }
        if (avatar instanceof Map<?, ?> avatarMap) {
            Object avatarValue = avatarMap.get("avatar");
            if (avatarValue != null && !String.valueOf(avatarValue).isBlank()) {
                return String.valueOf(avatarValue);
            }
        }
        return fallback;
    }

    private String getString(Map<String, Object> source, String key, String fallback) {
        if (source == null || source.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(source.get(key));
        return value.isBlank() ? fallback : value;
    }

    private Integer getInteger(Map<String, Object> source, String key, Integer fallback) {
        if (source == null || source.get(key) == null) {
            return fallback;
        }
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
