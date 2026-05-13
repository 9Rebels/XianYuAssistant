package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CaptchaUrlProbeService {

    private static final String MTOP_LOGIN_TOKEN_API = "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/";
    private static final String APP_KEY = "34839810";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 通过 Cookie 主动探测当前账号是否需要验证，以及最新的 verification_url。
     * 返回 null 表示不需要验证或探测失败。
     */
    public ProbeResult probe(String cookieText) {
        if (cookieText == null || cookieText.isBlank()) {
            return null;
        }
        try {
            Map<String, String> cookies = XianyuSignUtils.parseCookies(cookieText);
            String token = XianyuSignUtils.extractToken(cookies);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String dataVal = "{}";
            String sign = XianyuSignUtils.generateSign(timestamp, token, dataVal);

            HttpUrl url = HttpUrl.parse(MTOP_LOGIN_TOKEN_API).newBuilder()
                    .addQueryParameter("jsv", "2.7.2")
                    .addQueryParameter("appKey", APP_KEY)
                    .addQueryParameter("t", timestamp)
                    .addQueryParameter("sign", sign)
                    .addQueryParameter("v", "1.0")
                    .addQueryParameter("type", "originaljson")
                    .addQueryParameter("accountSite", "xianyu")
                    .addQueryParameter("dataType", "json")
                    .addQueryParameter("timeout", "20000")
                    .addQueryParameter("api", "mtop.taobao.idlemessage.pc.login.token")
                    .addQueryParameter("sessionOption", "AutoLoginOnly")
                    .build();

            RequestBody body = new FormBody.Builder()
                    .add("data", dataVal)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Cookie", cookieText)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.goofish.com/")
                    .header("Origin", "https://www.goofish.com")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode root = objectMapper.readTree(responseBody);

                JsonNode retNode = root.path("ret");
                if (retNode.isArray()) {
                    for (JsonNode ret : retNode) {
                        String retStr = ret.asText("");
                        if (retStr.contains("SUCCESS")) {
                            log.debug("[CaptchaProbe] Cookie 有效，无需验证");
                            return ProbeResult.valid();
                        }
                        if (retStr.contains("RGV587_ERROR") || retStr.contains("FAIL_SYS_USER_VALIDATE")) {
                            String verificationUrl = root.path("data").path("url").asText(null);
                            log.info("[CaptchaProbe] 需要验证, url={}", verificationUrl);
                            return ProbeResult.needVerification(verificationUrl);
                        }
                    }
                }
                log.debug("[CaptchaProbe] 未知响应: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
                return null;
            }
        } catch (Exception e) {
            log.warn("[CaptchaProbe] 探测失败: {}", e.getMessage());
            return null;
        }
    }

    public static class ProbeResult {
        private final boolean valid;
        private final String verificationUrl;

        private ProbeResult(boolean valid, String verificationUrl) {
            this.valid = valid;
            this.verificationUrl = verificationUrl;
        }

        public static ProbeResult valid() { return new ProbeResult(true, null); }
        public static ProbeResult needVerification(String url) { return new ProbeResult(false, url); }

        public boolean isValid() { return valid; }
        public boolean needsVerification() { return !valid; }
        public String getVerificationUrl() { return verificationUrl; }
    }
}
