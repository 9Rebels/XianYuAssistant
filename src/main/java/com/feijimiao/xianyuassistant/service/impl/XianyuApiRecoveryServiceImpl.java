package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.AccountIdentityGuard;
import com.feijimiao.xianyuassistant.service.CookieRecoveryService;
import com.feijimiao.xianyuassistant.service.CookieRefreshService;
import com.feijimiao.xianyuassistant.service.XianyuApiRecoveryService;
import com.feijimiao.xianyuassistant.service.bo.CookieRecoveryResult;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryRequest;
import com.feijimiao.xianyuassistant.service.bo.XianyuApiRecoveryResult;
import com.feijimiao.xianyuassistant.utils.XianyuApiUtils;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class XianyuApiRecoveryServiceImpl implements XianyuApiRecoveryService {
    private static final int MAX_RECOVERY_ATTEMPTS = 1;
    private static final String DEFAULT_VERSION = "1.0";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CookieRecoveryService cookieRecoveryService;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private CookieRefreshService cookieRefreshService;

    @Autowired
    private AccountIdentityGuard accountIdentityGuard;

    @Override
    public XianyuApiRecoveryResult callApi(XianyuApiRecoveryRequest request) {
        validateRequest(request);
        String cookie = resolveCookie(request);
        accountIdentityGuard.assertCookieBelongsToAccount(request.getAccountId(), cookie);
        return callApi(request, cookie, 0, false);
    }

    private XianyuApiRecoveryResult callApi(XianyuApiRecoveryRequest request, String cookie,
                                            int recoveryAttempts, boolean recovered) {
        if (missingToken(cookie)) {
            return recoverAndRetry(request, cookie, "Cookie中缺少_m_h5_tk", recoveryAttempts, recovered);
        }

        XianyuApiUtils.ApiCallResultWithHeaders headersResult = doCall(request, cookie);
        String mergedCookie = mergeResponseCookies(request.getAccountId(), cookie, headersResult);
        String response = headersResult != null ? headersResult.getBody() : null;
        if (response == null || response.isBlank()) {
            return XianyuApiRecoveryResult.failed(null, "闲鱼接口无响应", null);
        }

        String retText = extractRetText(response);
        if (isRecoverableRet(retText)) {
            return recoverAndRetry(request, mergedCookie, retText, recoveryAttempts, recovered);
        }
        if (isSuccessRet(retText)) {
            return XianyuApiRecoveryResult.success(response, mergedCookie, recovered, headersResult);
        }
        return XianyuApiRecoveryResult.failed(response, retText.isBlank() ? "闲鱼接口返回异常" : retText, null);
    }

    private XianyuApiUtils.ApiCallResultWithHeaders doCall(XianyuApiRecoveryRequest request, String cookie) {
        String version = request.getVersion() == null || request.getVersion().isBlank()
                ? DEFAULT_VERSION
                : request.getVersion();
        return XianyuApiUtils.callApiWithHeaders(
                request.getApiName(),
                request.getDataMap(),
                cookie,
                request.getSpmCnt(),
                request.getSpmPre(),
                version);
    }

    private XianyuApiRecoveryResult recoverAndRetry(XianyuApiRecoveryRequest request, String cookie,
                                                    String reason, int recoveryAttempts, boolean recovered) {
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            String message = "已尝试自动刷新和验证，仍失败，请人工更新Cookie或完成滑块验证";
            return XianyuApiRecoveryResult.failed(null, message, CookieRecoveryResult.failed(message));
        }

        CookieRecoveryResult recovery = cookieRecoveryService.recover(
                request.getAccountId(), request.getOperationName(), reason);
        if (!recovery.isSuccess()) {
            return XianyuApiRecoveryResult.failed(null, recovery.getMessage(), recovery);
        }

        String nextCookie = recovery.getCookieText();
        if (nextCookie == null || nextCookie.isBlank()) {
            nextCookie = getCookieByAccountId(request.getAccountId());
        }
        if (nextCookie == null || nextCookie.isBlank()) {
            return XianyuApiRecoveryResult.failed(null, "Cookie刷新成功但重读Cookie失败", recovery);
        }
        if (!accountIdentityGuard.canUseCookie(request.getAccountId(), nextCookie)) {
            return XianyuApiRecoveryResult.failed(null, "Cookie刷新后身份与当前账号不一致，已拒绝继续操作", recovery);
        }
        return callApi(request, nextCookie, recoveryAttempts + 1, true);
    }

    private String mergeResponseCookies(Long accountId, String currentCookie,
                                        XianyuApiUtils.ApiCallResultWithHeaders headersResult) {
        if (headersResult == null || headersResult.getSetCookieHeaders().isEmpty()) {
            return currentCookie;
        }
        String mergedCookie = mergeSetCookies(currentCookie, headersResult.getSetCookieHeaders());
        mergedCookie = cookieRefreshService.clearDuplicateCookies(mergedCookie);
        if (!mergedCookie.equals(currentCookie)) {
            if (!accountIdentityGuard.canUseCookie(accountId, mergedCookie)) {
                log.warn("【账号{}】闲鱼API响应Cookie身份不匹配，已拒绝写回", accountId);
                return currentCookie;
            }
            updateCookie(accountId, mergedCookie);
            log.info("【账号{}】闲鱼API响应Set-Cookie已合并保存", accountId);
        }
        return mergedCookie;
    }

    private String mergeSetCookies(String cookie, List<String> setCookieHeaders) {
        Map<String, String> cookies = new LinkedHashMap<>(XianyuSignUtils.parseCookies(cookie));
        for (String setCookie : setCookieHeaders) {
            mergeSetCookie(cookies, setCookie);
        }
        return XianyuSignUtils.formatCookies(cookies);
    }

    private void mergeSetCookie(Map<String, String> cookies, String setCookie) {
        if (setCookie == null || setCookie.isBlank()) {
            return;
        }
        int semiIndex = setCookie.indexOf(';');
        String firstPart = semiIndex >= 0 ? setCookie.substring(0, semiIndex) : setCookie;
        int equalIndex = firstPart.indexOf('=');
        if (equalIndex <= 0) {
            return;
        }
        String key = firstPart.substring(0, equalIndex).trim();
        String value = firstPart.substring(equalIndex + 1).trim();
        if (value.isEmpty()) {
            cookies.remove(key);
            return;
        }
        cookies.put(key, value);
    }

    private String extractRetText(String response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            Object ret = responseMap.get("ret");
            if (ret instanceof List<?> list && !list.isEmpty()) {
                return String.valueOf(list.get(0));
            }
            return ret == null ? "" : String.valueOf(ret);
        } catch (Exception e) {
            log.warn("解析闲鱼API ret失败: {}", e.getMessage());
            return "";
        }
    }

    private boolean isSuccessRet(String retText) {
        return retText != null && retText.contains("SUCCESS");
    }

    private boolean isRecoverableRet(String retText) {
        if (retText == null) {
            return false;
        }
        return retText.contains("FAIL_SYS_TOKEN_EXOIRED")
                || retText.contains("FAIL_SYS_TOKEN_EXPIRED")
                || retText.contains("FAIL_SYS_SESSION_EXPIRED")
                || retText.contains("SID_INVALID")
                || retText.contains("_m_h5_tk")
                || retText.contains("令牌过期")
                || retText.contains("Cookie失效")
                || retText.contains("Cookie过期")
                || retText.contains("RGV587")
                || retText.contains("FAIL_SYS_RGV587_ERROR")
                || retText.contains("FAIL_SYS_USER_VALIDATE")
                || retText.contains("USER_VALIDATE")
                || retText.contains("被挤爆")
                || retText.contains("滑块")
                || retText.contains("风控");
    }

    private boolean missingToken(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return true;
        }
        Map<String, String> cookies = XianyuSignUtils.parseCookies(cookie);
        String token = XianyuSignUtils.extractToken(cookies);
        return token == null || token.isBlank();
    }

    private String resolveCookie(XianyuApiRecoveryRequest request) {
        if (request.getCookie() != null && !request.getCookie().isBlank()) {
            return request.getCookie();
        }
        String cookie = getCookieByAccountId(request.getAccountId());
        if (cookie != null && !cookie.isBlank()) {
            return cookie;
        }
        throw new IllegalArgumentException("未找到账号Cookie，请先登录");
    }

    private String getCookieByAccountId(Long accountId) {
        XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
        if (cookie == null || cookie.getCookieText() == null || cookie.getCookieText().isBlank()) {
            return null;
        }
        return cookie.getCookieText();
    }

    private void updateCookie(Long accountId, String cookieText) {
        if (!accountIdentityGuard.canUseCookie(accountId, cookieText)) {
            log.warn("【账号{}】准备保存的API Cookie身份不匹配，已拒绝写回", accountId);
            return;
        }
        XianyuCookie cookie = cookieMapper.selectByAccountId(accountId);
        if (cookie == null) {
            log.warn("【账号{}】未找到Cookie记录，无法保存闲鱼API响应Set-Cookie", accountId);
            return;
        }
        cookie.setCookieText(cookieText);
        cookie.setCookieStatus(1);
        cookie.setUpdatedTime(LocalDateTime.now().format(DATETIME_FORMATTER));
        cookieMapper.updateById(cookie);
    }

    private void validateRequest(XianyuApiRecoveryRequest request) {
        if (request == null || request.getAccountId() == null) {
            throw new IllegalArgumentException("账号ID不能为空");
        }
        if (request.getApiName() == null || request.getApiName().isBlank()) {
            throw new IllegalArgumentException("闲鱼API名称不能为空");
        }
        if (request.getDataMap() == null) {
            throw new IllegalArgumentException("闲鱼API请求参数不能为空");
        }
    }
}
