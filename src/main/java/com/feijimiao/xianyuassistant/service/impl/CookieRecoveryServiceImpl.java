package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.exception.CaptchaRequiredException;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.CaptchaService;
import com.feijimiao.xianyuassistant.service.CookieRecoveryService;
import com.feijimiao.xianyuassistant.service.CookieRefreshService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class CookieRecoveryServiceImpl implements CookieRecoveryService {
    private static final String CAPTCHA_TARGET_URL = "https://www.goofish.com/im";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private CookieRefreshService cookieRefreshService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Override
    public CookieRecoveryResult recover(Long accountId, String operationName, String reason) {
        if (accountId == null) {
            return CookieRecoveryResult.failed("缺少账号ID，无法自动恢复Cookie");
        }

        String detectedDesc = buildDetectedDesc(operationName, reason);
        log.warn("【账号{}】{}: {}", accountId, detectedDesc, reason);
        logOperation(accountId, detectedDesc, OperationConstants.Status.PARTIAL, reason);

        try {
            boolean refreshSuccess = cookieRefreshService.refreshCookie(accountId);
            if (refreshSuccess) {
                String latestCookie = getLatestCookieText(accountId);
                if (latestCookie != null && !latestCookie.isBlank()) {
                    logOperation(accountId, "Cookie刷新成功，继续原操作", OperationConstants.Status.SUCCESS, null);
                    return CookieRecoveryResult.success(latestCookie, "Cookie已自动刷新，继续执行" + safeOperationName(operationName));
                }
                log.warn("【账号{}】Cookie刷新成功但重读Cookie失败", accountId);
            }
            logOperation(accountId, "hasLogin刷新失败，进入浏览器兜底",
                    OperationConstants.Status.PARTIAL, reason);
        } catch (CaptchaRequiredException e) {
            logOperation(accountId, "hasLogin刷新失败，进入浏览器兜底",
                    OperationConstants.Status.PARTIAL, e.getMessage());
            return tryAutoCaptcha(accountId, reason + "; " + e.getMessage());
        } catch (Exception e) {
            log.error("【账号{}】自动恢复Cookie异常", accountId, e);
            logOperation(accountId, "hasLogin刷新失败，进入浏览器兜底",
                    OperationConstants.Status.PARTIAL, e.getMessage());
        }

        CookieRecoveryResult captchaResult = tryAutoCaptcha(accountId, reason);

        markUnrecoverable(accountId, reason);
        String message = captchaResult.getMessage() == null
                ? CaptchaService.AUTO_SLIDER_FAILED_MESSAGE
                : captchaResult.getMessage();
        logOperation(accountId, "自动恢复失败，请人工更新Cookie", OperationConstants.Status.FAIL, reason);
        return CookieRecoveryResult.failed(message);
    }

    private CookieRecoveryResult tryAutoCaptcha(Long accountId, String reason) {
        String cookieText = getLatestCookieText(accountId);
        if (cookieText == null || cookieText.isBlank()) {
            return CookieRecoveryResult.failed("未找到可用于滑块验证的Cookie，请人工更新Cookie");
        }

        try {
            CaptchaService.CaptchaResult captcha = captchaService.handleRequiredCaptcha(
                    accountId, cookieText, CAPTCHA_TARGET_URL);
            if (captcha.isAutoVerifySuccess()) {
                boolean refreshSuccess = cookieRefreshService.refreshCookie(accountId);
                if (refreshSuccess) {
                    String latestCookie = getLatestCookieText(accountId);
                    if (latestCookie != null && !latestCookie.isBlank()) {
                        logOperation(accountId, "滑块自动验证成功，继续原操作",
                                OperationConstants.Status.SUCCESS, null);
                        return CookieRecoveryResult.success(latestCookie, "滑块自动验证成功，继续执行原操作");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("【账号{}】自动滑块验证失败: {}", accountId, e.getMessage());
        }
        logOperation(accountId, "触发风控/滑块，需要人工更新Cookie",
                OperationConstants.Status.FAIL, reason);
        return CookieRecoveryResult.failed(CaptchaService.AUTO_SLIDER_FAILED_MESSAGE);
    }

    private String getLatestCookieText(Long accountId) {
        XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
        return cookie != null ? cookie.getCookieText() : null;
    }

    private void markUnrecoverable(Long accountId, String reason) {
        if (isRiskReason(reason)) {
            updateCookieStatus(accountId, 3);
            return;
        }
        updateCookieStatus(accountId, 2);
    }

    private void updateCookieStatus(Long accountId, Integer cookieStatus) {
        XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
        if (cookie == null) {
            log.warn("【账号{}】未找到Cookie记录，无法标记状态为{}", accountId, cookieStatus);
            return;
        }
        cookie.setCookieStatus(cookieStatus);
        cookie.setUpdatedTime(LocalDateTime.now().format(DATETIME_FORMATTER));
        cookieMapper.updateById(cookie);
    }

    private String buildDetectedDesc(String operationName, String reason) {
        String prefix = safeOperationName(operationName);
        if (isRiskReason(reason)) {
            return prefix + "检测到Cookie失效/风控，开始自动恢复";
        }
        return prefix + "检测到Token过期，开始自动恢复";
    }

    private String safeOperationName(String operationName) {
        if (operationName == null || operationName.isBlank()) {
            return "";
        }
        return operationName + "：";
    }

    private boolean isRiskReason(String reason) {
        if (reason == null) {
            return false;
        }
        return reason.contains("RGV587")
                || reason.contains("USER_VALIDATE")
                || reason.contains("被挤爆")
                || reason.contains("风控")
                || reason.contains("滑块")
                || reason.contains("验证");
    }

    private void logOperation(Long accountId, String desc, Integer status, String errorMessage) {
        operationLogService.log(accountId,
                OperationConstants.Type.REFRESH,
                OperationConstants.Module.COOKIE,
                desc,
                status,
                OperationConstants.TargetType.COOKIE,
                String.valueOf(accountId),
                null, null, errorMessage, null);
    }
}
