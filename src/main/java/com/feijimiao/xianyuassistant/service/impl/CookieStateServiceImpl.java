package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.enums.CookieStatus;
import com.feijimiao.xianyuassistant.event.cookie.CookieExpiredNotificationEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.CookieStateService;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class CookieStateServiceImpl implements CookieStateService {

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public XianyuCookie latestCookie(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return cookieMapper.selectOne(new LambdaQueryWrapper<XianyuCookie>()
                .eq(XianyuCookie::getXianyuAccountId, accountId)
                .orderByDesc(XianyuCookie::getCreatedTime)
                .last("LIMIT 1"));
    }

    @Override
    public boolean markValid(Long accountId) {
        return updateStatus(accountId, CookieStatus.VALID, false, "Cookie恢复有效");
    }

    @Override
    public boolean markExpired(Long accountId, boolean sendNotify) {
        return updateStatus(accountId, CookieStatus.EXPIRED, sendNotify, "Cookie过期");
    }

    @Override
    public boolean markInvalid(Long accountId, String reason) {
        if (reason != null && !reason.isBlank()) {
            log.warn("【账号{}】Cookie标记为失效: {}", accountId, reason);
        }
        return updateStatus(accountId, CookieStatus.INVALID, false, reason);
    }

    @Override
    public boolean updateStatus(Long accountId, CookieStatus status, boolean sendNotify) {
        return updateStatus(accountId, status, sendNotify, status != null ? status.getDescription() : null);
    }

    private boolean updateStatus(Long accountId, CookieStatus status, boolean sendNotify, String reason) {
        if (accountId == null || status == null) {
            log.warn("Cookie状态更新参数无效: accountId={}, status={}", accountId, status);
            return false;
        }
        try {
            XianyuCookie currentCookie = latestCookie(accountId);
            if (currentCookie == null) {
                log.warn("【账号{}】未找到Cookie记录，无法更新状态为{}", accountId, status.getDescription());
                return false;
            }
            Integer oldStatus = currentCookie.getCookieStatus();
            if (sendNotify && status == CookieStatus.EXPIRED && isHealthy(currentCookie)) {
                log.info("【账号{}】Cookie过期通知发送前复核为健康状态，跳过状态更新和过期通知", accountId);
                return true;
            }
            if (currentCookie.getId() == null) {
                log.warn("【账号{}】最新Cookie缺少ID，无法精确更新状态为{}", accountId, status.getDescription());
                return false;
            }
            String now = DateTimeUtils.currentShanghaiTime();
            int rows = updateLatestCookieStatus(currentCookie.getId(), status, reason, now);
            log.info("【账号{}】Cookie状态已更新为{}({}), rows={}",
                    accountId, status.getDescription(), status.getCode(), rows);
            publishCookieExpiredNotificationIfNeeded(accountId, oldStatus, status, sendNotify, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("【账号{}】Cookie状态更新失败: {}", accountId, status.getDescription(), e);
            return false;
        }
    }

    private int updateLatestCookieStatus(Long cookieId, CookieStatus status, String reason, String now) {
        LambdaUpdateWrapper<XianyuCookie> wrapper = baseStatusUpdateWrapper(cookieId, status, now)
                .set(XianyuCookie::getStateReason, reason)
                .set(XianyuCookie::getStateUpdatedTime, now);
        try {
            return cookieMapper.update(null, wrapper);
        } catch (DataAccessException e) {
            if (!isMissingStateColumn(e)) {
                throw e;
            }
            log.debug("【Cookie{}】状态灰度字段未就绪，已降级只写基础状态字段", cookieId);
            return cookieMapper.update(null, baseStatusUpdateWrapper(cookieId, status, now));
        }
    }

    private LambdaUpdateWrapper<XianyuCookie> baseStatusUpdateWrapper(Long cookieId,
                                                                     CookieStatus status,
                                                                     String now) {
        return new LambdaUpdateWrapper<XianyuCookie>()
                .eq(XianyuCookie::getId, cookieId)
                .set(XianyuCookie::getCookieStatus, status.getCode())
                .set(XianyuCookie::getUpdatedTime, now);
    }

    private boolean isMissingStateColumn(DataAccessException e) {
        String message = e.getMessage();
        return message != null
                && (message.contains("state_reason") || message.contains("state_updated_time"));
    }

    @Override
    public boolean isHealthy(XianyuCookie cookie) {
        if (cookie == null || !CookieStatus.isValid(cookie.getCookieStatus())) {
            return false;
        }
        return hasText(cookie.getCookieText())
                && hasText(cookie.getMH5Tk())
                && hasText(cookie.getWebsocketToken())
                && cookie.getTokenExpireTime() != null
                && cookie.getTokenExpireTime() > System.currentTimeMillis();
    }

    private void publishCookieExpiredNotificationIfNeeded(Long accountId, Integer oldStatus,
                                                          CookieStatus status, boolean sendNotify, int rows) {
        if (status != CookieStatus.EXPIRED || Objects.equals(oldStatus, CookieStatus.EXPIRED.getCode())) {
            return;
        }
        if (!sendNotify) {
            log.info("【账号{}】Cookie被标记为过期，但系统将尝试自动续期，暂不发送通知", accountId);
            return;
        }
        if (rows <= 0) {
            log.warn("【账号{}】Cookie过期状态未更新成功，跳过过期通知事件", accountId);
            return;
        }
        eventPublisher.publishEvent(new CookieExpiredNotificationEvent(accountId));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
