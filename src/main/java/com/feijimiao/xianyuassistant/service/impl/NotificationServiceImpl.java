package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.entity.XianyuNotificationLog;
import com.feijimiao.xianyuassistant.mapper.XianyuNotificationLogMapper;
import com.feijimiao.xianyuassistant.service.EmailNotifyService;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.utils.DateTimeUtils;
import com.feijimiao.xianyuassistant.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知服务，按当前选择的通道发送一条通知。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final String KEY_ENABLED = "notify_webhook_enabled";
    private static final String KEY_CHANNEL = "notify_channel";
    private static final String KEY_DISPATCH_MODE = "notify_dispatch_mode";
    private static final String KEY_WEBHOOK_TYPE = "notify_webhook_type";
    private static final String KEY_WEBHOOK_URL = "notify_webhook_url";
    private static final String KEY_SUCCESS = "notify_auto_delivery_success";
    private static final String KEY_FAIL = "notify_auto_delivery_fail";
    private static final String KEY_STOCK = "notify_stock_warning";
    private static final String KEY_HOURLY_REPORT = "notify_hourly_report_enabled";
    private static final String KEY_WS_DISCONNECT = "email_notify_ws_disconnect_enabled";
    private static final String KEY_COOKIE_EXPIRE = "email_notify_cookie_expire_enabled";
    private static final String KEY_CAPTCHA_REQUIRED = "notify_captcha_required_enabled";
    private static final String KEY_CAPTCHA_SUCCESS = "notify_captcha_success_enabled";

    private static final String OFFICIAL_WECOM_WEBHOOK =
            "https://qyapi.weixin.qq.com/cgi-bin/webhook/send";
    private static final String OFFICIAL_WECOM_API = "https://qyapi.weixin.qq.com";
    private static final String DISPATCH_ALL = "all";
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("账号ID[：:]\\s*(\\d+)");

    @Autowired
    private SysSettingService sysSettingService;

    @Autowired
    private XianyuNotificationLogMapper notificationLogMapper;

    @Autowired
    private EmailNotifyService emailNotifyService;

    @Autowired
    private com.feijimiao.xianyuassistant.sse.SseEventBus sseEventBus;

    @Autowired
    private OperationLogService operationLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void notifyEvent(String eventType, String title, String content) {
        if (!isEventEnabled(eventType)) {
            saveLog(eventType, title, content, "local", 0, "通知开关未开启");
            return;
        }
        List<NotificationResult> results = sendConfigured(eventType, title, content, false);
        saveLogs(eventType, title, content, results);
    }

    @Override
    public boolean sendTest(String title, String content) {
        List<NotificationResult> results = sendConfigured(EVENT_TEST, title, content, true);
        saveLogs(EVENT_TEST, title, content, results);
        return results.stream().allMatch(result -> result.status() == 1);
    }

    @Override
    public List<XianyuNotificationLog> latestLogs() {
        return notificationLogMapper.selectLatest();
    }

    private List<NotificationResult> sendConfigured(String eventType, String title, String content, boolean ignoreGlobalSwitch) {
        if (!ignoreGlobalSwitch && !"1".equals(setting(KEY_ENABLED))) {
            return List.of(NotificationResult.fail("local", "通知总开关未开启"));
        }

        List<NotificationResult> results = new ArrayList<>();
        for (String channel : dispatchChannels()) {
            results.add(sendToChannel(channel, eventType, title, content));
        }
        return results;
    }

    private NotificationResult sendToChannel(String channel, String eventType, String title, String content) {
        try {
            String response = switch (channel) {
                case "feishu" -> sendJson(urlSetting("notify_feishu_url", KEY_WEBHOOK_URL),
                        Map.of("msg_type", "text", "content", Map.of("text", text(title, content))));
                case "dingtalk" -> sendJson(dingTalkUrl(),
                        Map.of("msgtype", "text", "text", Map.of("content", text(title, content))));
                case "dingtalk_signed" -> sendJson(signedDingTalkUrl(), 
                        Map.of("msgtype", "text", "text", Map.of("content", text(title, content))));
                case "wecom" -> sendJson(wecomWebhookUrl(),
                        Map.of("msgtype", "text", "text", Map.of("content", text(title, content))));
                case "wecom_app" -> sendWecomApp(title, content);
                case "bark" -> sendJson(required("notify_bark_url", "Bark地址"),
                        barkPayload(title, content));
                case "pushplus" -> sendJson("https://www.pushplus.plus/send",
                        pushPlusPayload(title, content));
                case "wxpusher" -> sendJson("https://wxpusher.zjiecode.com/api/send/message",
                        wxPusherPayload(title, content));
                case "pushdeer" -> HttpClientUtils.get(pushDeerUrl(title, content), null);
                case "serverchan" -> sendForm(serverChanUrl(), Map.of("title", title, "desp", content));
                case "telegram" -> sendJson(telegramUrl(),
                        Map.of("chat_id", required("notify_telegram_chat_id", "Telegram Chat ID"),
                                "text", text(title, content)));
                case "gotify" -> sendForm(gotifyUrl(), gotifyBody(title, content));
                case "email" -> sendEmail(title, content);
                default -> sendJson(urlSetting("notify_generic_url", KEY_WEBHOOK_URL),
                        genericPayload(eventType, title, content));
            };
            return response == null ? NotificationResult.fail(channel, "通知请求失败")
                    : NotificationResult.success(channel);
        } catch (Exception e) {
            log.warn("发送通知失败: channel={}, eventType={}", channel, eventType, e);
            return NotificationResult.fail(channel, e.getMessage());
        }
    }

    private boolean isEventEnabled(String eventType) {
        String key = switch (eventType) {
            case EVENT_DELIVERY_SUCCESS -> KEY_SUCCESS;
            case EVENT_DELIVERY_FAIL -> KEY_FAIL;
            case EVENT_STOCK_WARNING -> KEY_STOCK;
            case EVENT_HOURLY_REPORT -> KEY_HOURLY_REPORT;
            case EVENT_WS_DISCONNECT -> KEY_WS_DISCONNECT;
            case EVENT_COOKIE_EXPIRE -> KEY_COOKIE_EXPIRE;
            case EVENT_CAPTCHA_REQUIRED -> KEY_CAPTCHA_REQUIRED;
            case EVENT_CAPTCHA_SUCCESS -> KEY_CAPTCHA_SUCCESS;
            default -> null;
        };
        return key != null && "1".equals(setting(key));
    }

    private List<String> dispatchChannels() {
        if (!DISPATCH_ALL.equals(setting(KEY_DISPATCH_MODE))) {
            return List.of(selectedChannel());
        }
        List<String> channels = configuredChannels();
        return channels.isEmpty() ? List.of(selectedChannel()) : channels;
    }

    private List<String> configuredChannels() {
        List<String> channels = new ArrayList<>();
        addIfConfigured(channels, "generic", hasSetting("notify_generic_url") || hasSetting(KEY_WEBHOOK_URL));
        addIfConfigured(channels, "feishu", hasSetting("notify_feishu_url"));
        addIfConfigured(channels, hasSetting("notify_dingtalk_secret") ? "dingtalk_signed" : "dingtalk",
                hasSetting("notify_dingtalk_token") || hasSetting("notify_dingtalk_url"));
        addIfConfigured(channels, "wecom", hasSetting("notify_wecom_key"));
        addIfConfigured(channels, "wecom_app", hasSettings("notify_wecom_app_corpid",
                "notify_wecom_app_secret", "notify_wecom_app_touser", "notify_wecom_app_agentid"));
        addIfConfigured(channels, "bark", hasSetting("notify_bark_url"));
        addIfConfigured(channels, "pushplus", hasSetting("notify_pushplus_token"));
        addIfConfigured(channels, "wxpusher", hasSetting("notify_wxpusher_app_token"));
        addIfConfigured(channels, "pushdeer", hasSetting("notify_pushdeer_token"));
        addIfConfigured(channels, "serverchan", hasSetting("notify_serverchan_token"));
        addIfConfigured(channels, "telegram", hasSettings("notify_telegram_bot_token", "notify_telegram_chat_id"));
        addIfConfigured(channels, "gotify", hasSettings("notify_gotify_url", "notify_gotify_token"));
        addIfConfigured(channels, "email", emailNotifyService.isEmailConfigured());
        return channels;
    }

    private void addIfConfigured(List<String> channels, String channel, boolean configured) {
        if (configured) { channels.add(channel); }
    }

    private String selectedChannel() {
        String value = setting(KEY_CHANNEL);
        if (value == null || value.isBlank()) {
            value = setting(KEY_WEBHOOK_TYPE);
        }
        if (value == null || value.isBlank()) {
            return "generic";
        }
        value = value.trim().toLowerCase();
        return switch (value) {
            case "generic", "feishu", "dingtalk", "dingtalk_signed", "wecom", "wecom_app",
                    "bark", "pushplus", "wxpusher", "pushdeer", "serverchan",
                    "telegram", "gotify", "email" -> value;
            default -> "generic";
        };
    }

    private String sendJson(String url, Map<String, Object> payload) throws Exception {
        return HttpClientUtils.postJson(url, Map.of("Content-Type", "application/json"),
                objectMapper.writeValueAsString(payload));
    }

    private String sendForm(String url, Map<String, String> body) {
        return HttpClientUtils.post(url, Map.of("Content-Type", "application/x-www-form-urlencoded"), body);
    }

    private Map<String, Object> genericPayload(String eventType, String title, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private Map<String, Object> barkPayload(String title, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("body", content);
        putIfPresent(payload, "group", setting("notify_bark_group"));
        putIfPresent(payload, "sound", setting("notify_bark_sound"));
        return payload;
    }

    private Map<String, Object> pushPlusPayload(String title, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", required("notify_pushplus_token", "PushPlus Token"));
        payload.put("title", title);
        payload.put("content", content);
        payload.put("template", "txt");
        putIfPresent(payload, "topic", setting("notify_pushplus_topic"));
        return payload;
    }

    private Map<String, Object> wxPusherPayload(String title, String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("appToken", required("notify_wxpusher_app_token", "WxPusher AppToken"));
        payload.put("content", text(title, content));
        payload.put("summary", title);
        payload.put("contentType", 1);
        putList(payload, "uids", splitTargets(setting("notify_wxpusher_uids")));
        putList(payload, "topicIds", splitTargets(setting("notify_wxpusher_topic_ids")));
        putIfPresent(payload, "url", setting("notify_wxpusher_url"));
        return payload;
    }

    private String pushDeerUrl(String title, String content) {
        String base = setting("notify_pushdeer_custom_url");
        if (base == null || base.isBlank()) {
            base = "https://api2.pushdeer.com/message/push";
        }
        return withQuery(base, Map.of(
                "pushkey", required("notify_pushdeer_token", "PushDeer Key"),
                "text", title,
                "desp", content,
                "type", "markdown"
        ));
    }

    private String serverChanUrl() {
        String token = required("notify_serverchan_token", "Server酱 SendKey");
        return "https://sctapi.ftqq.com/" + token + ".send";
    }

    private String telegramUrl() {
        String apiBase = setting("notify_telegram_api_base");
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.telegram.org";
        }
        return trimRight(apiBase, "/") + "/bot" + required("notify_telegram_bot_token", "Telegram Bot Token")
                + "/sendMessage";
    }

    private String gotifyUrl() {
        String base = trimRight(required("notify_gotify_url", "Gotify地址"), "/");
        return base + "/message?token=" + encode(required("notify_gotify_token", "Gotify Token"));
    }

    private Map<String, String> gotifyBody(String title, String content) {
        return Map.of(
                "title", title,
                "message", content,
                "priority", defaultValue(setting("notify_gotify_priority"), "0")
        );
    }

    private String sendEmail(String title, String content) {
        String error = emailNotifyService.sendNotificationEmail(title, content);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        return "OK";
    }

    private String signedDingTalkUrl() throws Exception {
        String url = dingTalkUrl();
        String secret = setting("notify_dingtalk_secret");
        if (secret == null || secret.isBlank()) {
            return url;
        }
        long timestamp = System.currentTimeMillis();
        String signText = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sign = Base64.getEncoder().encodeToString(mac.doFinal(signText.getBytes(StandardCharsets.UTF_8)));
        return appendParam(appendParam(url, "timestamp", String.valueOf(timestamp)), "sign", sign);
    }

    private String dingTalkUrl() {
        String token = setting("notify_dingtalk_token");
        if (token != null && !token.isBlank()) {
            return "https://oapi.dingtalk.com/robot/send?access_token=" + encode(token.trim());
        }
        return urlSetting("notify_dingtalk_url", KEY_WEBHOOK_URL);
    }

    private String wecomWebhookUrl() {
        String key = setting("notify_wecom_key");
        String legacyUrl = setting(KEY_WEBHOOK_URL);
        if ((key == null || key.isBlank()) && legacyUrl != null && legacyUrl.contains("qyapi.weixin.qq.com")) {
            return legacyUrl.trim();
        }
        key = required("notify_wecom_key", "企业微信机器人 Key");
        return OFFICIAL_WECOM_WEBHOOK + "?key=" + encode(key);
    }

    @SuppressWarnings("unchecked")
    private String sendWecomApp(String title, String content) throws Exception {
        String apiBase = wecomAppApiBase();
        boolean officialApi = OFFICIAL_WECOM_API.equals(apiBase);
        Map<String, String> tokenParams = Map.of(
                "corpid", required("notify_wecom_app_corpid", "企业微信 Corpid"),
                "corpsecret", required("notify_wecom_app_secret", "企业微信 CorpSecret")
        );
        String tokenResponse = officialApi
                ? HttpClientUtils.get(withQuery(apiBase + "/cgi-bin/gettoken", tokenParams), null)
                : sendJson(apiBase + "/cgi-bin/gettoken", new HashMap<>(tokenParams));
        if (tokenResponse == null && !officialApi) {
            tokenResponse = HttpClientUtils.get(withQuery(apiBase + "/cgi-bin/gettoken", tokenParams), null);
        }
        if (tokenResponse == null) {
            throw new IllegalStateException("企业微信应用获取 access_token 失败: 接口无响应");
        }
        Map<String, Object> tokenMap = objectMapper.readValue(tokenResponse, new TypeReference<>() {});
        Object accessToken = tokenMap.get("access_token");
        if (accessToken == null || String.valueOf(accessToken).isBlank()) {
            Object errMsg = tokenMap.get("errmsg");
            Object errCode = tokenMap.get("errcode");
            throw new IllegalStateException("企业微信应用 access_token 为空: errcode="
                    + errCode + ", errmsg=" + errMsg);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("touser", required("notify_wecom_app_touser", "企业微信接收成员"));
        payload.put("agentid", required("notify_wecom_app_agentid", "企业微信应用 AgentId"));
        String mediaId = setting("notify_wecom_app_media_id");
        if (mediaId == null || mediaId.isBlank()) {
            payload.put("msgtype", "text");
            payload.put("text", Map.of("content", text(title, content)));
        } else {
            payload.put("msgtype", "mpnews");
            payload.put("mpnews", Map.of("articles", List.of(Map.of(
                    "title", title,
                    "thumb_media_id", mediaId.trim(),
                    "author", "闲鱼助手",
                    "content_source_url", "",
                    "content", content.replace("\n", "<br/>"),
                    "digest", content.length() > 120 ? content.substring(0, 120) : content
            ))));
        }
        payload.put("safe", 0);
        return sendJson(apiBase + "/cgi-bin/message/send?access_token=" + encode(String.valueOf(accessToken)), payload);
    }

    private String wecomAppApiBase() {
        String customUrl = setting("notify_wecom_app_custom_url");
        if (customUrl == null || customUrl.isBlank()) {
            return OFFICIAL_WECOM_API;
        }
        String value = trimRight(customUrl.trim(), "/");
        if (value.endsWith("/cgi-bin/gettoken")) {
            return value.substring(0, value.length() - "/cgi-bin/gettoken".length());
        }
        if (value.endsWith("/cgi-bin/message/send")) {
            return value.substring(0, value.length() - "/cgi-bin/message/send".length());
        }
        return value;
    }

    private String urlSetting(String key, String fallbackKey) {
        String value = setting(key);
        if (value == null || value.isBlank()) {
            value = setting(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("通知地址未配置");
        }
        return value.trim();
    }

    private String required(String key, String label) {
        String value = setting(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "未配置");
        }
        return value.trim();
    }

    private String setting(String key) {
        return sysSettingService.getSettingValue(key);
    }

    private boolean hasSetting(String key) {
        String value = setting(key); return value != null && !value.isBlank();
    }

    private boolean hasSettings(String... keys) {
        for (String key : keys) {
            if (!hasSetting(key)) {
                return false;
            }
        }
        return true;
    }

    private String text(String title, String content) {
        return title + "\n" + content;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private void putList(Map<String, Object> target, String key, List<String> values) {
        if (!values.isEmpty()) {
            target.put(key, values);
        }
    }

    private List<String> splitTargets(String value) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return values;
        }
        for (String item : value.split("[,;\\n]")) {
            if (!item.trim().isEmpty()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private String withQuery(String url, Map<String, String> params) {
        String result = url;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = appendParam(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String appendParam(String url, String key, String value) {
        String joiner = url.contains("?") ? "&" : "?";
        return url + joiner + encode(key) + "=" + encode(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String trimRight(String value, String suffix) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }

    private void saveLog(String eventType, String title, String content, String channel, Integer status, String error) {
        XianyuNotificationLog logRecord = new XianyuNotificationLog();
        logRecord.setEventType(eventType);
        logRecord.setTitle(title);
        logRecord.setContent(content);
        logRecord.setChannel(channel);
        logRecord.setStatus(status);
        logRecord.setErrorMessage(error);
        logRecord.setCreateTime(DateTimeUtils.currentShanghaiTime());
        notificationLogMapper.insert(logRecord);
        logNotificationOperation(eventType, title, content, channel, status, error);
    }

    private void logNotificationOperation(String eventType, String title, String content, String channel, Integer status, String error) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("eventType", eventType);
            params.put("title", title);
            params.put("channel", channel);
            params.put("contentLength", content != null ? content.length() : 0);
            operationLogService.log(
                    resolveAccountId(content),
                    OperationConstants.Type.SEND,
                    OperationConstants.Module.SYSTEM,
                    status != null && status == 1 ? "通知发送成功" : "通知发送失败/跳过",
                    status != null && status == 1 ? OperationConstants.Status.SUCCESS : OperationConstants.Status.FAIL,
                    "NOTIFICATION",
                    eventType,
                    objectMapper.writeValueAsString(params),
                    null,
                    error,
                    null);
        } catch (Exception e) {
            log.warn("记录通知操作日志失败: eventType={}, channel={}", eventType, channel, e);
        }
    }

    private Long resolveAccountId(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = ACCOUNT_ID_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveLogs(String eventType, String title, String content, List<NotificationResult> results) {
        for (NotificationResult result : results) {
            saveLog(eventType, title, content, result.channel(), result.status(), result.error());
        }
        try {
            sseEventBus.broadcast("notification", java.util.Map.of(
                    "eventType", eventType,
                    "title", title,
                    "content", content.length() > 200 ? content.substring(0, 200) : content,
                    "accountId", Optional.ofNullable(resolveAccountId(content)).orElse(0L)
            ));
        } catch (Exception e) {
            log.debug("SSE通知推送异常: {}", e.getMessage());
        }
    }

    private record NotificationResult(String channel, int status, String error) {
        static NotificationResult success(String channel) { return new NotificationResult(channel, 1, null); }

        static NotificationResult fail(String channel, String error) { return new NotificationResult(channel, -1, error); }
    }
}
