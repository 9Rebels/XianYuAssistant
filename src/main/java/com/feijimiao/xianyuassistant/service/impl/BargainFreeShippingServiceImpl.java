package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.AccountIdentityGuard;
import com.feijimiao.xianyuassistant.service.BargainFreeShippingService;
import com.feijimiao.xianyuassistant.utils.SessionCookieJar;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class BargainFreeShippingServiceImpl implements BargainFreeShippingService {

    private static final String API_NAME = "mtop.idle.groupon.activity.seller.freeshipping";
    private static final String API_VERSION = "1.0";
    private static final String APP_KEY = "34839810";
    private static final String DEFAULT_API_URL = "https://h5api.m.goofish.com/h5/" + API_NAME + "/" + API_VERSION + "/";
    private static final int MAX_ATTEMPTS = 4;

    @Autowired
    private XianyuCookieMapper cookieMapper;

    @Autowired
    private com.feijimiao.xianyuassistant.utils.AccountProxyHelper accountProxyHelper;

    @Autowired
    private AccountIdentityGuard accountIdentityGuard;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String apiUrl = DEFAULT_API_URL;
    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public FreeShippingResult freeShipping(FreeShippingRequest request) {
        validateInput(request);
        XianyuCookie cookie = cookieMapper.selectByAccountId(request.accountId());
        if (cookie == null || isBlank(cookie.getCookieText())) {
            throw new IllegalStateException("账号Cookie不存在");
        }

        String cookieText = cookie.getCookieText();
        if (!accountIdentityGuard.canUseCookie(request.accountId(), cookieText)) {
            throw new IllegalStateException("账号Cookie身份不匹配，已拒绝调用免拼接口");
        }
        FreeShippingResult lastResult = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SessionCookieJar cookieJar = new SessionCookieJar(cookieText);
            try {
                String responseBody = executeRequest(cookieJar, request);
                updateCookieIfChanged(request.accountId(), cookie, cookieText, cookieJar.getCookieString());
                cookieText = cookieJar.getCookieString();
                lastResult = parseResult(responseBody);
                if (lastResult.success()) {
                    log.info("【账号{}】免拼API调用成功: orderId={}, itemId={}",
                            request.accountId(), request.orderId(), request.itemId());
                    return lastResult;
                }
                log.warn("【账号{}】免拼API调用失败，第{}次: {}", request.accountId(), attempt, lastResult.message());
            } catch (IOException e) {
                lastResult = new FreeShippingResult(false, "网络异常: " + e.getMessage(), null);
                log.warn("【账号{}】免拼API网络异常，第{}次: {}", request.accountId(), attempt, e.getMessage());
            }
        }
        return lastResult != null ? lastResult : new FreeShippingResult(false, "免拼API调用失败", null);
    }

    String executeRequest(SessionCookieJar cookieJar, FreeShippingRequest request) throws IOException {
        String dataJson = buildDataJson(request);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String token = cookieJar.getMh5tkToken();
        if (isBlank(token)) {
            throw new IllegalStateException("Cookie中缺少_m_h5_tk，请重新登录");
        }
        String url = buildUrl(timestamp, XianyuSignUtils.generateSign(timestamp, token, dataJson));
        Request httpRequest = new Request.Builder()
                .url(url)
                .headers(okhttp3.Headers.of(buildHeaders()))
                .post(new FormBody.Builder().add("data", dataJson).build())
                .build();
        try (Response response = accountProxyHelper.applyProxy(
                okHttpClient.newBuilder().cookieJar(cookieJar), request.accountId()
        ).build().newCall(httpRequest).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() < 200 || response.code() >= 300) {
                throw new IOException("HTTP异常: " + response.code() + ", " + body);
            }
            return body;
        }
    }

    String buildDataJson(FreeShippingRequest request) {
        return "{\"bizOrderId\":\"" + request.orderId() + "\", \"itemId\":"
                + request.itemId() + ",\"buyerId\":" + request.buyerId() + "}";
    }

    String buildUrl(String timestamp, String sign) {
        StringBuilder url = new StringBuilder(apiUrl).append('?');
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", APP_KEY);
        params.put("t", timestamp);
        params.put("sign", sign);
        params.put("v", API_VERSION);
        params.put("type", "originaljson");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", API_NAME);
        params.put("sessionOption", "AutoLoginOnly");
        params.forEach((key, value) -> url.append(key).append('=').append(value).append('&'));
        return url.substring(0, url.length() - 1);
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Origin", "https://h5.m.goofish.com");
        headers.put("Referer", "https://h5.m.goofish.com/");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        return headers;
    }

    private FreeShippingResult parseResult(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode ret = root.get("ret");
            String message = ret != null && ret.isArray() && ret.size() > 0 ? ret.get(0).asText() : "未知响应";
            return new FreeShippingResult("SUCCESS::调用成功".equals(message), message, responseBody);
        } catch (Exception e) {
            return new FreeShippingResult(false, "解析免拼API响应失败: " + e.getMessage(), responseBody);
        }
    }

    private void updateCookieIfChanged(Long accountId, XianyuCookie cookie, String oldCookieText, String newCookieText) {
        if (isBlank(newCookieText) || newCookieText.equals(oldCookieText)) {
            return;
        }
        if (!accountIdentityGuard.canUseCookie(accountId, newCookieText)) {
            log.warn("【账号{}】免拼API返回Cookie身份不匹配，已拒绝写回", accountId);
            return;
        }
        cookie.setCookieText(newCookieText);
        cookie.setCookieStatus(1);
        cookie.setMH5Tk(XianyuSignUtils.parseCookies(newCookieText).get("_m_h5_tk"));
        cookieMapper.updateById(cookie);
    }

    private void validateInput(FreeShippingRequest request) {
        if (request == null || request.accountId() == null || isBlank(request.orderId())
                || isBlank(request.itemId()) || isBlank(request.buyerId())) {
            throw new IllegalArgumentException("免拼参数缺失");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
