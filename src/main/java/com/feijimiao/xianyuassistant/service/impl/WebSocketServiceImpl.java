package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.config.WebSocketConfig;
import com.feijimiao.xianyuassistant.constants.OperationConstants;
import com.feijimiao.xianyuassistant.event.account.AccountRemovedEvent;
import com.feijimiao.xianyuassistant.service.AccountService;
import com.feijimiao.xianyuassistant.service.NotificationService;
import com.feijimiao.xianyuassistant.service.OperationLogService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.WebSocketTokenService;
import com.feijimiao.xianyuassistant.utils.XianyuSignUtils;
import com.feijimiao.xianyuassistant.websocket.WebSocketInitializer;
import com.feijimiao.xianyuassistant.websocket.WebSocketMessageHandler;
import com.feijimiao.xianyuassistant.websocket.XianyuWebSocketClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket服务实现类
 * 参考Python代码的XianyuAutoAsync类
 * 增强功能：
 * 1. Token自动刷新机制
 * 2. 心跳超时检测
 * 3. 连接重连机制
 */
@Slf4j
@Service
public class WebSocketServiceImpl implements WebSocketService {

    @Autowired
    private AccountService accountService;
    
    @Autowired
    private WebSocketMessageHandler messageHandler;
    
    @Autowired
    private WebSocketTokenService tokenService;
    
    @Autowired
    private WebSocketInitializer initializer;
    
    @Autowired
    private WebSocketConfig config;
    
    @Autowired
    private com.feijimiao.xianyuassistant.utils.AccountDisplayNameUtils displayNameUtils;
    
    @Autowired
    private OperationLogService operationLogService;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.CookieRefreshService cookieRefreshService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationContentBuilder notificationContentBuilder;

    @Autowired
    private com.feijimiao.xianyuassistant.utils.AccountProxyHelper accountProxyHelper;

    @Autowired
    private com.feijimiao.xianyuassistant.sse.SseEventBus sseEventBus;

    // 存储WebSocket客户端
    private final Map<Long, XianyuWebSocketClient> webSocketClients = new ConcurrentHashMap<>();
    
    // 心跳定时器
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(2);
    
    // 心跳任务
    private final Map<Long, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    
    // Token刷新定时器
    private final ScheduledExecutorService tokenRefreshScheduler = Executors.newScheduledThreadPool(1);
    
    // Token刷新任务
    private final Map<Long, ScheduledFuture<?>> tokenRefreshTasks = new ConcurrentHashMap<>();
    
    // 心跳响应时间记录
    private final Map<Long, Long> lastHeartbeatResponseTimes = new ConcurrentHashMap<>();
    
    // 心跳发送时间记录（参考Python的last_heartbeat_time）
    private final Map<Long, Long> lastHeartbeatSendTimes = new ConcurrentHashMap<>();
    
    // Token刷新时间记录
    private final Map<Long, Long> lastTokenRefreshTimes = new ConcurrentHashMap<>();
    
    // 连接重启标志
    private final Map<Long, Boolean> connectionRestartFlags = new ConcurrentHashMap<>();
    
    // 重连任务（防止重复重连）
    private final Map<Long, Future<?>> reconnectTasks = new ConcurrentHashMap<>();

    // 重连任务代次：每次调用 scheduleReconnect 都递增，回调内通过比对此代次决定是否仍是"当前任务"
    // 修复"已有延迟任务在执行就 return 导致新原因被吞掉"的丢任务问题
    private final Map<Long, AtomicLong> reconnectGenerations = new ConcurrentHashMap<>();
    
    // 重连执行器（参考Python的异步重连）
    private final ScheduledExecutorService reconnectExecutor = Executors.newScheduledThreadPool(2);
    
    // 重连次数记录（参考Python的无限重连但有退避）
    private final Map<Long, AtomicInteger> reconnectAttemptCounts = new ConcurrentHashMap<>();

    // 邮件通知防抖记录（避免频繁发送）
    private final Map<Long, Long> lastDisconnectNotifyTimes = new ConcurrentHashMap<>();

    // 重连失败达到此次数后触发邮件通知
    private static final int RECONNECT_NOTIFY_THRESHOLD = 3;

    // 邮件通知最小间隔（10分钟）
    private static final long NOTIFY_INTERVAL_MS = 10 * 60 * 1000;

    /**
     * 闲鱼WebSocket URL
     * 参考Python代码：wss://wss-goofish.dingtalk.com/
     */
    private static final String WEBSOCKET_URL = "wss://wss-goofish.dingtalk.com/";

    @Override
    public boolean startWebSocket(Long accountId) {
        try {
            log.info("启动WebSocket连接: accountId={}", accountId);

            // 检查是否已经连接
            if (webSocketClients.containsKey(accountId)) {
                XianyuWebSocketClient existingClient = webSocketClients.get(accountId);
                if (existingClient.isConnected()) {
                    log.info("WebSocket已连接: accountId={}", accountId);
                    return true;
                } else {
                    // 关闭旧连接
                    stopWebSocket(accountId);
                }
            }

            String cookieStr = loadCookieForStartup(accountId);

            // 解析Cookie
            Map<String, String> cookies = XianyuSignUtils.parseCookies(cookieStr);
            
            // 生成设备ID（参考Python的generate_device_id）
            String unb = cookies.get("unb");
            if (unb == null || unb.isEmpty()) {
                log.error("Cookie中缺少unb字段: accountId={}", accountId);
                throw new com.feijimiao.xianyuassistant.exception.CookieExpiredException("Cookie中缺少unb字段，Cookie可能已过期或无效");
            }
            // 使用持久化的设备ID（如果数据库中已有则使用，否则生成新的并保存）
            String deviceId = accountService.getOrGenerateDeviceId(accountId, unb);
            if (deviceId == null || deviceId.isEmpty()) {
                log.error("获取或生成设备ID失败: accountId={}", accountId);
                throw new RuntimeException("无法获取或生成设备ID");
            }
            log.info("使用设备ID: accountId={}, deviceId={}", accountId, deviceId);
            
            // 获取accessToken（参考Python的refresh_token）
            log.info("正在获取accessToken: accountId={}", accountId);
            String accessToken = tokenService.getAccessToken(accountId);
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("获取accessToken失败: accountId={}", accountId);
                log.error("无法继续WebSocket连接，请检查Cookie是否有效");
                throw new com.feijimiao.xianyuassistant.exception.TokenInvalidException("无法获取WebSocket Token，请检查Cookie是否有效");
            }
            log.info("accessToken获取成功: accountId={}, token长度={}", accountId, accessToken.length());
            
            // 调用通用连接方法
            return connectWebSocket(accountId, cookieStr, deviceId, accessToken, unb);

        } catch (com.feijimiao.xianyuassistant.exception.CaptchaRequiredException e) {
            log.warn("启动WebSocket需要滑块验证: accountId={}, url={}", accountId, e.getCaptchaUrl());
            throw e; // 重新抛出，让Controller处理
        } catch (Exception e) {
            log.error("启动WebSocket失败: accountId={}", accountId, e);
            return false;
        }
    }

    @Override
    public boolean startWebSocketWithToken(Long accountId, String accessToken) {
        try {
            log.info("========== 使用手动Token启动WebSocket连接 ==========");
            log.info("【账号{}】accountId={}", accountId, accountId);
            log.info("【账号{}】accessToken长度={}", accountId, accessToken != null ? accessToken.length() : 0);
            log.info("【账号{}】accessToken前50字符={}", accountId, 
                    accessToken != null && accessToken.length() > 50 ? accessToken.substring(0, 50) + "..." : accessToken);

            // 检查是否已经连接
            if (webSocketClients.containsKey(accountId)) {
                XianyuWebSocketClient existingClient = webSocketClients.get(accountId);
                if (existingClient.isConnected()) {
                    log.info("【账号{}】WebSocket已连接", accountId);
                    return true;
                } else {
                    // 关闭旧连接
                    log.info("【账号{}】关闭旧连接", accountId);
                    stopWebSocket(accountId);
                }
            }

            String cookieStr = loadCookieForStartup(accountId);
            log.info("【账号{}】Cookie长度={}", accountId, cookieStr.length());

            // 解析Cookie
            Map<String, String> cookies = XianyuSignUtils.parseCookies(cookieStr);
            log.info("【账号{}】解析到{}个Cookie字段", accountId, cookies.size());
            
            // 生成设备ID
            String unb = cookies.get("unb");
            if (unb == null || unb.isEmpty()) {
                log.error("【账号{}】Cookie中缺少unb字段", accountId);
                throw new com.feijimiao.xianyuassistant.exception.CookieExpiredException("Cookie中缺少unb字段，Cookie可能已过期或无效");
            }
            // 使用持久化的设备ID
            String deviceId = accountService.getOrGenerateDeviceId(accountId, unb);
            if (deviceId == null || deviceId.isEmpty()) {
                log.error("【账号{}】获取或生成设备ID失败", accountId);
                throw new RuntimeException("无法获取或生成设备ID");
            }
            log.info("【账号{}】设备ID={}", accountId, deviceId);
            
            log.info("【账号{}】准备调用通用连接方法（Token将在注册成功后保存）...", accountId);
            
            // 调用通用连接方法
            boolean result = connectWebSocket(accountId, cookieStr, deviceId, accessToken, unb);
            
            log.info("【账号{}】连接结果={}", accountId, result);
            log.info("========== 手动Token启动流程结束 ==========");
            
            return result;

        } catch (Exception e) {
            log.error("【账号{}】使用手动Token启动WebSocket失败", accountId, e);
            return false;
        }
    }

    private String loadCookieForStartup(Long accountId) {
        String cookieStr = accountService.getCookieByAccountId(accountId);
        if (cookieStr != null && !cookieStr.isEmpty()) {
            return cookieStr;
        }

        log.warn("【账号{}】启动WebSocket时未找到有效Cookie，先尝试自动刷新Cookie...", accountId);
        boolean refreshed = cookieRefreshService != null && cookieRefreshService.refreshCookie(accountId);
        if (refreshed) {
            cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr != null && !cookieStr.isEmpty()) {
                log.info("【账号{}】启动前Cookie自动刷新成功，继续连接", accountId);
                return cookieStr;
            }
            log.warn("【账号{}】Cookie自动刷新返回成功，但仍未读取到有效Cookie", accountId);
        } else {
            log.warn("【账号{}】启动前Cookie自动刷新失败", accountId);
        }

        throw new com.feijimiao.xianyuassistant.exception.CookieNotFoundException(
            "未找到有效Cookie，自动刷新失败，请手动更新Cookie");
    }

    /**
     * 通用WebSocket连接方法
     */
    boolean connectWebSocket(Long accountId, String cookieStr, String deviceId, String accessToken, String unb) throws Exception {
        try {
            // 构建WebSocket请求头（参考Python的WEBSOCKET_HEADERS配置）
            Map<String, String> headers = new HashMap<>();
            headers.put("Cookie", cookieStr);
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36");
            headers.put("Origin", "https://www.goofish.com");
            headers.put("Host", "wss-goofish.dingtalk.com");
            headers.put("Accept-Encoding", "gzip, deflate, br, zstd");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");
            headers.put("Cache-Control", "no-cache");
            headers.put("Pragma", "no-cache");
            headers.put("Connection", "Upgrade");
            headers.put("Upgrade", "websocket");

            // 创建WebSocket客户端（参考Python的_create_websocket_connection）
            URI serverUri = new URI(WEBSOCKET_URL);
            XianyuWebSocketClient client = new XianyuWebSocketClient(serverUri, headers, String.valueOf(accountId), displayNameUtils);
            
            // 设置当前用户ID（从Cookie的unb字段获取）
            client.setMyUserId(unb);
            
            // 设置消息处理器
            client.setMessageHandler(messageHandler);
            
            // 设置注册成功回调（保存Token）
            final String finalAccessToken = accessToken;
            client.setOnRegistrationSuccess(() -> {
                log.info("【账号{}】注册成功回调被触发，开始保存Token到数据库", accountId);
                tokenService.saveToken(accountId, finalAccessToken);
                log.info("【账号{}】✅ Token已成功保存到数据库", accountId);
            });
            
            // 设置Token失效回调（自动重连）
            // 参考Python: Token失效时设置connection_restart_flag=True，关闭WebSocket触发重连
            client.setOnTokenExpired(() -> {
                log.warn("【账号{}】Token失效(401)，触发自动重连流程...", accountId);
                try {
                    String newToken = tokenService.refreshToken(accountId, true);
                    if (newToken == null || newToken.isBlank()) {
                        log.error("【账号{}】Token失效后刷新失败，保留当前状态并等待后续重试", accountId);
                        scheduleReconnect(accountId, config.getReconnectDelay(), false);
                        return;
                    }

                    connectionRestartFlags.put(accountId, true);
                    stopWebSocket(accountId);
                    log.info("【账号{}】使用刷新后的Token重新启动WebSocket连接", accountId);
                    boolean success = startWebSocketWithToken(accountId, newToken);
                    
                    if (success) {
                        log.info("【账号{}】✅ Token失效后自动重连成功", accountId);
                    } else {
                        log.error("【账号{}】❌ Token失效后自动重连失败，将通过重连机制继续尝试", accountId);
                        // 参考Python: 失败后重连机制会继续尝试
                    }
                } catch (com.feijimiao.xianyuassistant.exception.CaptchaRequiredException e) {
                    log.warn("【账号{}】Token失效后刷新触发人机验证，停止自动重连: {}", accountId, e.getCaptchaUrl());
                } catch (Exception e) {
                    log.error("【账号{}】Token失效自动重连异常", accountId, e);
                    // 参考Python: 异常后外层while True会继续重试
                    scheduleReconnect(accountId, config.getReconnectDelay(), false);
                }
            });
            
            // 设置心跳响应回调（更新心跳响应时间）
            client.setOnHeartbeatResponse(() -> {
                updateHeartbeatResponseTime(accountId);
            });
            
            // 设置连接关闭回调（参考Python的finally块中重连逻辑）
            client.setOnConnectionClosed(() -> {
                log.warn("【账号{}】WebSocket连接被关闭，触发自动重连...", accountId);
                // 参考Python: 如果是主动重启，立即重连；否则等待5秒
                Boolean restartFlag = connectionRestartFlags.get(accountId);
                boolean isManualRestart = restartFlag != null && restartFlag;
                
                int delay = isManualRestart ? 0 : config.getReconnectDelay();
                scheduleReconnect(accountId, delay, isManualRestart);
            });

            // 连接WebSocket（参考Python的connect方法）
            log.info("正在连接WebSocket: {}", WEBSOCKET_URL);
            log.info("请求头: {}", headers);

            java.net.Proxy proxy = accountProxyHelper.resolveProxy(accountId);
            if (proxy != java.net.Proxy.NO_PROXY) {
                client.setProxy(proxy);
                log.info("【账号{}】WebSocket使用代理: {}", accountId, proxy.address());
            }

            boolean connected = client.connectBlocking(10, TimeUnit.SECONDS);
            
            if (connected) {
                webSocketClients.put(accountId, client);
                
                // 执行WebSocket初始化流程（参考Python的init方法）
                log.info("开始WebSocket初始化流程: accountId={}", accountId);
                initializer.initialize(client, accessToken, deviceId, String.valueOf(accountId));
                
                // 启动心跳任务
                startHeartbeat(accountId, client);
                
                log.info("WebSocket连接成功: accountId={}", accountId);
                log.info("连接状态: isOpen={}, isClosed={}",
                        client.isOpen(), client.isClosed());
                sseEventBus.broadcast("connection", java.util.Map.of("accountId", accountId, "connected", true));
                
                // 记录操作日志
                operationLogService.log(accountId, 
                    OperationConstants.Type.CONNECT, 
                    OperationConstants.Module.WEBSOCKET,
                    "WebSocket连接成功", 
                    OperationConstants.Status.SUCCESS,
                    OperationConstants.TargetType.WEBSOCKET, 
                    String.valueOf(accountId),
                    null, null, null, null);
                
                return true;
            } else {
                log.error("WebSocket连接失败: accountId={}", accountId);
                log.error("连接状态: isOpen={}, isClosed={}", 
                        client.isOpen(), client.isClosed());
                
                // 记录操作日志
                operationLogService.log(accountId, 
                    OperationConstants.Type.CONNECT, 
                    OperationConstants.Module.WEBSOCKET,
                    "WebSocket连接失败", 
                    OperationConstants.Status.FAIL,
                    OperationConstants.TargetType.WEBSOCKET, 
                    String.valueOf(accountId),
                    null, null, null, null);
                
                return false;
            }
        } catch (Exception e) {
            log.error("连接WebSocket异常: accountId={}", accountId, e);
            
            // 记录操作日志
            operationLogService.log(accountId, 
                OperationConstants.Type.CONNECT, 
                OperationConstants.Module.WEBSOCKET,
                "WebSocket连接异常: " + e.getMessage(), 
                OperationConstants.Status.FAIL,
                OperationConstants.TargetType.WEBSOCKET, 
                String.valueOf(accountId),
                null, null, null, null);
            
            throw e;
        }
    }

    @Override
    public boolean stopWebSocket(Long accountId) {
        try {
            log.info("停止WebSocket连接: accountId={}", accountId);

            // 停止心跳任务
            stopHeartbeat(accountId);

            // 关闭WebSocket连接
            XianyuWebSocketClient client = webSocketClients.remove(accountId);
            if (client != null) {
                // 标记为主动关闭，防止onClose回调触发自动重连
                // 重连由调用方自行决定是否需要
                client.setIntentionalClose(true);
                client.close();
                log.info("WebSocket连接已关闭: accountId={}", accountId);
                sseEventBus.broadcast("connection", java.util.Map.of("accountId", accountId, "connected", false));
                
                // 记录操作日志
                operationLogService.log(accountId, 
                    OperationConstants.Type.DISCONNECT, 
                    OperationConstants.Module.WEBSOCKET,
                    "WebSocket连接已关闭", 
                    OperationConstants.Status.SUCCESS,
                    OperationConstants.TargetType.WEBSOCKET, 
                    String.valueOf(accountId),
                    null, null, null, null);
                
                return true;
            } else {
                log.warn("WebSocket连接不存在: accountId={}", accountId);
                return false;
            }

        } catch (Exception e) {
            log.error("停止WebSocket失败: accountId={}", accountId, e);
            return false;
        }
    }

    @Override
    public boolean isConnected(Long accountId) {
        XianyuWebSocketClient client = webSocketClients.get(accountId);
        return client != null && client.isConnected();
    }

    @Override
    public void stopAllWebSockets() {
        log.info("停止所有WebSocket连接");

        // 快照遍历，避免边遍历边修改（stopWebSocket 内部会 webSocketClients.remove）
        for (Long accountId : new ArrayList<>(webSocketClients.keySet())) {
            stopWebSocket(accountId);
        }

        // 关闭心跳调度器
        heartbeatScheduler.shutdown();
    }

    /**
     * 启动心跳任务
     * 增强功能：心跳超时检测（完全对齐Python逻辑）
     * 
     * Python心跳逻辑：
     * 1. 每隔heartbeat_interval秒发送一次心跳
     * 2. 检查上次心跳响应时间，如果超过(heartbeat_interval + heartbeat_timeout)则认为连接断开
     * 3. 心跳循环break后，外层while True循环会自动重连
     */
    private void startHeartbeat(Long accountId, XianyuWebSocketClient client) {
        // 初始化心跳响应时间（秒级时间戳，对齐Python）
        long currentTime = System.currentTimeMillis() / 1000;
        lastHeartbeatResponseTimes.put(accountId, currentTime);
        lastHeartbeatSendTimes.put(accountId, currentTime);
        
        // 心跳发送任务（参考Python的heartbeat_loop）
        // 立即发送第一次心跳,防止连接空闲被关闭
        try {
            if (client.isConnected()) {
                client.sendHeartbeat();
                log.info("【账号{}】已发送初始心跳", accountId);
            }
        } catch (Exception e) {
            log.error("发送初始心跳失败: accountId={}", accountId, e);
        }
        
        // 参考Python: heartbeat_loop 中每1秒检查一次
        ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    long now = System.currentTimeMillis() / 1000;
                    
                    // 参考Python: 每隔heartbeat_interval秒发送一次心跳
                    Long lastSendTime = lastHeartbeatSendTimes.get(accountId);
                    if (lastSendTime == null || now - lastSendTime >= config.getHeartbeatInterval()) {
                        if (client.isConnected()) {
                            client.sendHeartbeat();
                            lastHeartbeatSendTimes.put(accountId, now);
                            log.debug("【账号{}】心跳已发送", accountId);
                        }
                    }
                    
                    // 参考Python: 检查上次心跳响应时间
                    // Python: if (current_time - self.last_heartbeat_response) > (self.heartbeat_interval + self.heartbeat_timeout):
                    Long lastResponseTime = lastHeartbeatResponseTimes.get(accountId);
                    if (lastResponseTime != null) {
                        long timeout = config.getHeartbeatInterval() + config.getHeartbeatTimeout();
                        
                        if (now - lastResponseTime > timeout) {
                            log.warn("【账号{}】心跳响应超时（{}秒无响应，超时阈值{}秒），连接可能已断开",
                                    accountId, now - lastResponseTime, timeout);
                            // 参考Python: heartbeat_loop break，触发外层重连
                            handleConnectionLost(accountId);
                        }
                    }
                } catch (Exception e) {
                    log.error("【账号{}】心跳任务异常", accountId, e);
                }
            },
            1, 1, TimeUnit.SECONDS  // 参考Python: 每秒检查一次
        );
        
        heartbeatTasks.put(accountId, heartbeatTask);
        log.info("心跳任务已启动: accountId={}, 心跳间隔{}秒, 超时阈值{}+{}秒", 
                accountId, config.getHeartbeatInterval(), config.getHeartbeatInterval(), config.getHeartbeatTimeout());
        
        // 启动Token自动刷新任务（参考Python的token_refresh_loop）
        startTokenRefresh(accountId);
    }
    
    /**
     * 启动Token自动刷新任务
     * 参考Python的token_refresh_loop方法
     * 
     * Python逻辑：
     * 1. 每分钟检查一次
     * 2. 当 current_time - last_token_refresh_time >= token_refresh_interval 时刷新Token
     * 3. Token刷新成功后，设置connection_restart_flag=True，关闭WebSocket触发重连
     * 4. Token刷新失败后，在token_retry_interval秒后重试
     * 
     * 关键修复：
     * - 记录Token获取时间（而非刷新时间），确保1小时后刷新
     * - Token有效期20小时，但每1小时主动刷新一次，保持连接活跃
     */
    private void startTokenRefresh(Long accountId) {
        // 初始化Token刷新时间为当前时间（秒级时间戳）
        long currentTime = System.currentTimeMillis() / 1000;
        lastTokenRefreshTimes.put(accountId, currentTime);
        
        log.info("【账号{}】Token刷新任务已启动: 刷新间隔{}秒({}小时), 首次刷新将在{}小时后", 
                accountId, config.getTokenRefreshInterval(), 
                config.getTokenRefreshInterval() / 3600,
                config.getTokenRefreshInterval() / 3600);
        
        // Token刷新任务（每分钟检查一次，参考Python）
        ScheduledFuture<?> tokenRefreshTask = tokenRefreshScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    if (tokenService.isCaptchaWaiting(accountId)) {
                        log.info("【账号{}】账号正在等待人机验证，暂停Token自动刷新任务", accountId);
                        stopTokenRefreshTask(accountId);
                        return;
                    }

                    Long lastRefreshTime = lastTokenRefreshTimes.get(accountId);
                    if (lastRefreshTime == null) {
                        return;
                    }
                    
                    long now = System.currentTimeMillis() / 1000;
                    long elapsedSeconds = now - lastRefreshTime;
                    
                    // 参考Python: 检查是否需要刷新Token（每1小时刷新一次）
                    if (elapsedSeconds >= config.getTokenRefreshInterval()) {
                        long elapsedHours = elapsedSeconds / 3600;
                        log.info("【账号{}】Token已使用{}小时（刷新间隔{}小时），准备刷新并重连...", 
                                accountId, elapsedHours, config.getTokenRefreshInterval() / 3600);
                        
                        // 参考Python: 设置连接重启标志
                        connectionRestartFlags.put(accountId, true);
                        
                        // 参考Python: 刷新Token并重连（成功后关闭旧连接）
                        refreshTokenAndReconnect(accountId);
                    } else {
                        // 每10分钟打印一次剩余时间（避免日志过多）
                        if (elapsedSeconds % 600 == 0) {
                            long remainingSeconds = config.getTokenRefreshInterval() - elapsedSeconds;
                            log.debug("【账号{}】Token刷新倒计时: 还有{}分钟", accountId, remainingSeconds / 60);
                        }
                    }
                } catch (Exception e) {
                    log.error("【账号{}】Token刷新检查失败", accountId, e);
                }
            },
            60, 60, TimeUnit.SECONDS  // 参考Python: 每分钟检查一次
        );
        
        tokenRefreshTasks.put(accountId, tokenRefreshTask);
    }
    
    /**
     * 刷新Token并重连
     * 参考Python的refresh_token和重连逻辑
     * 
     * Python逻辑：
     * 1. 刷新Token
     * 2. 设置connection_restart_flag = True
     * 3. 关闭当前WebSocket连接（触发重连）
     * 4. Token刷新失败时，在token_retry_interval后重试
     */
    private void refreshTokenAndReconnect(Long accountId) {
        try {
            if (tokenService.isCaptchaWaiting(accountId)) {
                log.info("【账号{}】账号正在等待人机验证，停止Token刷新重试", accountId);
                return;
            }

            log.info("【账号{}】开始刷新Token并重连...", accountId);
            
            // 参考Python: 刷新Token前先从数据库重新加载最新Cookie
            // 避免使用过期的Cookie导致刷新必然失败
            try {
                if (cookieRefreshService != null) {
                    log.info("【账号{}】刷新Token前先检查Cookie登录状态...", accountId);
                    // 使用静默检查，不记录操作日志（避免频繁记录）
                    boolean cookieOk = cookieRefreshService.checkLoginStatusQuietly(accountId);
                    if (!cookieOk) {
                        log.warn("【账号{}】Cookie已失效(hasLogin)，触发浏览器兜底刷新Cookie（对齐Python的_refresh_cookies_via_browser）...", accountId);
                        boolean browserRefreshOk = cookieRefreshService.refreshCookie(accountId);
                        if (browserRefreshOk) {
                            log.info("【账号{}】浏览器兜底刷新Cookie成功，继续重连", accountId);
                        } else {
                            log.error("【账号{}】hasLogin和浏览器兜底刷新Cookie均失败，Cookie可能已彻底过期", accountId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("【账号{}】刷新Token前Cookie检查/兜底刷新异常，继续尝试重连: {}", accountId, e.getMessage());
            }
            
            String newToken = tokenService.refreshToken(accountId, false);
            if (newToken == null || newToken.isBlank()) {
                if (tokenService.isCaptchaWaiting(accountId)) {
                    log.info("【账号{}】Token刷新进入人机验证等待，保留旧连接并停止自动重试", accountId);
                    return;
                }
                log.error("【账号{}】Token刷新失败，保留旧连接，将在{}秒后重试",
                        accountId, config.getTokenRetryInterval());
                reconnectExecutor.schedule(() -> {
                    log.info("【账号{}】Token刷新重试间隔已到，开始重试...", accountId);
                    refreshTokenAndReconnect(accountId);
                }, config.getTokenRetryInterval(), TimeUnit.SECONDS);
                return;
            }

            stopWebSocket(accountId);
            boolean success = startWebSocketWithToken(accountId, newToken);
            
            if (success) {
                // 更新Token刷新时间
                lastTokenRefreshTimes.put(accountId, System.currentTimeMillis() / 1000);
                // 重置重连计数
                AtomicInteger attemptCount = reconnectAttemptCounts.get(accountId);
                if (attemptCount != null) {
                    attemptCount.set(0);
                }
                log.info("【账号{}】✅ Token刷新并重连成功", accountId);
            } else if (tokenService.isCaptchaWaiting(accountId)) {
                log.info("【账号{}】Token刷新后进入人机验证等待，停止自动重试", accountId);
            } else {
                log.error("【账号{}】❌ Token刷新并重连失败，将在{}秒后重试", 
                        accountId, config.getTokenRetryInterval());
                
                // 参考Python: Token刷新失败后，在token_retry_interval后重试
                reconnectExecutor.schedule(() -> {
                    log.info("【账号{}】Token刷新重试间隔已到，开始重试...", accountId);
                    refreshTokenAndReconnect(accountId);
                }, config.getTokenRetryInterval(), TimeUnit.SECONDS);
            }
        } catch (com.feijimiao.xianyuassistant.exception.CaptchaRequiredException e) {
            log.warn("【账号{}】Token刷新并重连触发人机验证，停止自动重试: {}", accountId, e.getCaptchaUrl());
        } catch (Exception e) {
            if (tokenService.isCaptchaWaiting(accountId)) {
                log.info("【账号{}】账号正在等待人机验证，停止异常后的Token重试", accountId);
                return;
            }
            log.error("【账号{}】Token刷新并重连异常，将在{}秒后重试", 
                    accountId, config.getTokenRetryInterval(), e);
            
            // 参考Python: 异常后也要重试
            reconnectExecutor.schedule(() -> {
                log.info("【账号{}】Token刷新重试间隔已到，开始重试...", accountId);
                refreshTokenAndReconnect(accountId);
            }, config.getTokenRetryInterval(), TimeUnit.SECONDS);
        }
    }
    
    /**
     * 处理连接丢失
     * 参考Python的连接重连逻辑
     */
    private void handleConnectionLost(Long accountId) {
        log.warn("【账号{}】检测到连接丢失（心跳超时），准备重连...", accountId);
        scheduleReconnect(accountId, config.getReconnectDelay(), false);
    }
    
    /**
     * 调度重连任务
     * 参考Python的main()方法中while True无限重连循环
     *
     * 关键改进（对齐Python逻辑）：
     * 1. 新原因（401/心跳超时/Token 刷新）来临时取消旧延迟任务，按新延迟重新调度
     *    通过代次（generation）让"已开始执行的旧任务"自检退出，避免双连
     * 2. 指数退避：重连失败后延迟逐渐增加
     * 3. 重连成功后重置计数
     *
     * @param accountId 账号ID
     * @param delaySeconds 延迟秒数
     * @param isManualRestart 是否主动重启（Token刷新等）
     */
    private void scheduleReconnect(Long accountId, int delaySeconds, boolean isManualRestart) {
        if (tokenService.isCaptchaWaiting(accountId)) {
            log.info("【账号{}】账号正在等待人机验证，跳过重连调度", accountId);
            return;
        }

        // 递增代次：本次调度的"身份证"
        AtomicLong genCounter = reconnectGenerations.computeIfAbsent(accountId, k -> new AtomicLong());
        final long myGen = genCounter.incrementAndGet();

        // 取消未开始的旧任务；已开始的旧任务通过代次自检退出
        Future<?> existingTask = reconnectTasks.remove(accountId);
        if (existingTask != null && !existingTask.isDone()) {
            boolean cancelled = existingTask.cancel(false);
            log.info("【账号{}】发现旧重连任务，cancel={}，按新原因（{}秒，第{}代）重新调度",
                    accountId, cancelled, delaySeconds, myGen);
        }

        // 重置重启标志
        if (isManualRestart) {
            connectionRestartFlags.put(accountId, false);
        }

        // 获取/初始化重连次数
        AtomicInteger attemptCount = reconnectAttemptCounts.computeIfAbsent(accountId, k -> new AtomicInteger(0));

        // 参考Python: 无限重连，但使用指数退避
        int currentAttempt = attemptCount.incrementAndGet();
        // 指数退避: 5s, 10s, 20s, 40s, 60s, 60s, ... 最大60秒
        int actualDelay = isManualRestart ? delaySeconds :
                Math.min(delaySeconds * (int) Math.pow(2, Math.min(currentAttempt - 1, 4)), 60);

        log.info("【账号{}】计划{}秒后执行重连（第{}次尝试，gen={}）...",
                accountId, actualDelay, currentAttempt, myGen);

        ScheduledFuture<?> reconnectTask = reconnectExecutor.schedule(() -> {
            try {
                // 自检：是否仍为最新代次。旧代次任务在等待期间被新代次覆盖时直接退出，避免双连
                long currentGen = genCounter.get();
                if (currentGen != myGen) {
                    log.info("【账号{}】重连任务已被新代次替换（mine={}, current={}），跳过执行",
                            accountId, myGen, currentGen);
                    return;
                }
                reconnectTasks.remove(accountId);

                if (tokenService.isCaptchaWaiting(accountId)) {
                    log.info("【账号{}】账号正在等待人机验证，停止执行重连任务", accountId);
                    return;
                }

                // 停止当前连接和心跳
                stopWebSocket(accountId);

                // 参考Python: 重连前先刷新Cookie（hasLogin保活）
                try {
                    if (cookieRefreshService != null) {
                        log.info("【账号{}】重连前先检查Cookie登录状态...", accountId);
                        // 使用静默检查，不记录操作日志（避免频繁记录）
                        boolean cookieOk = cookieRefreshService.checkLoginStatusQuietly(accountId);
                        if (!cookieOk) {
                            log.warn("【账号{}】Cookie已失效(hasLogin)，重连前触发浏览器兜底刷新Cookie（对齐Python）...", accountId);
                            boolean browserRefreshOk = cookieRefreshService.refreshCookie(accountId);
                            if (browserRefreshOk) {
                                log.info("【账号{}】浏览器兜底刷新Cookie成功，继续重连", accountId);
                            } else {
                                log.error("【账号{}】hasLogin和浏览器兜底刷新Cookie均失败，重连可能失败", accountId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("【账号{}】重连前Cookie检查/兜底刷新异常，继续尝试重连: {}", accountId, e.getMessage());
                }

                // 重新启动连接
                boolean success = startWebSocket(accountId);

                if (success) {
                    // 重连成功，重置计数
                    attemptCount.set(0);
                    log.info("【账号{}】✅ 重连成功", accountId);

                    operationLogService.log(accountId,
                        OperationConstants.Type.RECONNECT,
                        OperationConstants.Module.WEBSOCKET,
                        isManualRestart ? "主动重启连接成功" : "异常断开后重连成功",
                        OperationConstants.Status.SUCCESS,
                        OperationConstants.TargetType.WEBSOCKET,
                        String.valueOf(accountId),
                        null, null, null, null);
                } else if (tokenService.isCaptchaWaiting(accountId)) {
                    log.info("【账号{}】重连过程中进入人机验证等待，停止继续重连", accountId);
                } else {
                    log.error("【账号{}】❌ 重连失败（第{}次），将继续尝试...", accountId, currentAttempt);

                    operationLogService.log(accountId,
                        OperationConstants.Type.RECONNECT,
                        OperationConstants.Module.WEBSOCKET,
                        "重连失败（第" + currentAttempt + "次）",
                        OperationConstants.Status.FAIL,
                        OperationConstants.TargetType.WEBSOCKET,
                        String.valueOf(accountId),
                        null, null, null, null);

                    // 重连失败达到阈值时触发邮件通知
                    if (currentAttempt >= RECONNECT_NOTIFY_THRESHOLD) {
                        triggerWsDisconnectNotify(accountId);
                    }

                    // 参考Python: 重连失败后继续尝试（while True循环）
                    scheduleReconnect(accountId, config.getReconnectDelay(), false);
                }
            } catch (com.feijimiao.xianyuassistant.exception.CaptchaRequiredException e) {
                log.warn("【账号{}】重连触发人机验证，停止继续重连: {}", accountId, e.getCaptchaUrl());
            } catch (Exception e) {
                if (tokenService.isCaptchaWaiting(accountId)) {
                    log.info("【账号{}】账号正在等待人机验证，停止异常后的重连重试", accountId);
                    return;
                }
                log.error("【账号{}】重连异常，将继续尝试...", accountId, e);
                scheduleReconnect(accountId, config.getReconnectDelay(), false);
            }
        }, actualDelay, TimeUnit.SECONDS);

        reconnectTasks.put(accountId, reconnectTask);
    }
    
    /**
     * 更新心跳响应时间
     * 由消息处理器调用
     */
    public void updateHeartbeatResponseTime(Long accountId) {
        lastHeartbeatResponseTimes.put(accountId, System.currentTimeMillis() / 1000);
    }

    /**
     * 停止心跳任务
     */
    private void stopHeartbeat(Long accountId) {
        // 停止心跳任务
        ScheduledFuture<?> heartbeatTask = heartbeatTasks.remove(accountId);
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            log.info("心跳任务已停止: accountId={}", accountId);
        }
        
        // 停止Token刷新任务
        stopTokenRefreshTask(accountId);
        
        // 取消重连任务
        Future<?> reconnectTask = reconnectTasks.remove(accountId);
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            log.info("重连任务已取消: accountId={}", accountId);
        }
        
        // 清理状态
        lastHeartbeatResponseTimes.remove(accountId);
        lastHeartbeatSendTimes.remove(accountId);
        lastTokenRefreshTimes.remove(accountId);
        connectionRestartFlags.remove(accountId);
    }

    private void stopTokenRefreshTask(Long accountId) {
        ScheduledFuture<?> tokenRefreshTask = tokenRefreshTasks.remove(accountId);
        if (tokenRefreshTask != null) {
            tokenRefreshTask.cancel(false);
            log.info("Token刷新任务已停止: accountId={}", accountId);
        }
    }

    @Override
    public boolean sendMessage(Long accountId, String cid, String toId, String text) {
        try {
            log.info("发送消息: accountId={}, cid={}, toId={}, text={}", accountId, cid, toId, text);
            
            // 获取WebSocket客户端
            XianyuWebSocketClient client = webSocketClients.get(accountId);
            if (client == null) {
                log.error("WebSocket客户端不存在: accountId={}", accountId);
                return false;
            }
            
            if (!client.isConnected()) {
                log.error("WebSocket未连接: accountId={}", accountId);
                return false;
            }
            
            // 发送消息
            client.sendMessage(cid, toId, text);
            return true;
            
        } catch (Exception e) {
            log.error("发送消息失败: accountId={}, cid={}, toId={}", accountId, cid, toId, e);
            return false;
        }
    }

    @Override
    public boolean sendMessageWithResult(Long accountId, String cid, String toId, String text) {
        try {
            log.info("发送消息(等待结果): accountId={}, cid={}, toId={}, text={}", accountId, cid, toId, text);
            
            XianyuWebSocketClient client = webSocketClients.get(accountId);
            if (client == null) {
                log.error("WebSocket客户端不存在: accountId={}", accountId);
                return false;
            }
            
            if (!client.isConnected()) {
                log.error("WebSocket未连接: accountId={}", accountId);
                return false;
            }
            
            return client.sendMessageWithResult(cid, toId, text);
            
        } catch (Exception e) {
            log.error("发送消息失败: accountId={}, cid={}, toId={}", accountId, cid, toId, e);
            return false;
        }
    }

    public void completePendingResponse(Long accountId, String mid, int code) {
        XianyuWebSocketClient client = webSocketClients.get(accountId);
        if (client != null) {
            client.completePendingResponse(mid, code);
        }
    }
    
    @Override
    public boolean sendImageMessage(Long accountId, String cid, String toId, String imageUrl, int width, int height) {
        try {
            log.info("发送图片消息: accountId={}, cid={}, toId={}, url={}, size={}x{}", 
                    accountId, cid, toId, imageUrl, width, height);
            
            XianyuWebSocketClient client = webSocketClients.get(accountId);
            if (client == null) {
                log.error("WebSocket客户端不存在: accountId={}", accountId);
                return false;
            }
            
            if (!client.isConnected()) {
                log.error("WebSocket未连接: accountId={}", accountId);
                return false;
            }
            
            client.sendImageMessage(cid, toId, imageUrl, width, height);
            return true;
            
        } catch (Exception e) {
            log.error("发送图片消息失败: accountId={}, cid={}, toId={}", accountId, cid, toId, e);
            return false;
        }
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        log.info("应用关闭，清理WebSocket资源");
        stopAllWebSockets();

        // 关闭Token刷新调度器
        tokenRefreshScheduler.shutdown();

        // 关闭重连调度器
        reconnectExecutor.shutdown();
    }

    /**
     * 账号删除事件处理：先断开连接，再清理 stopHeartbeat 未涉及的剩余状态
     *
     * <p>stopWebSocket → stopHeartbeat 已清理：心跳/Token刷新/重连任务、心跳响应/发送时间、Token刷新时间、连接重启标志。
     * 账号删除场景额外需要清理：重连次数计数、断开通知防抖记录、重连代次（避免幽灵代次干扰未来同 id 的账号）。</p>
     */
    @EventListener
    public void onAccountRemoved(AccountRemovedEvent event) {
        Long accountId = event.getAccountId();
        try {
            log.info("【账号{}】收到账号删除事件，断开 WebSocket 并清理连接状态", accountId);
            stopWebSocket(accountId);
            reconnectAttemptCounts.remove(accountId);
            lastDisconnectNotifyTimes.remove(accountId);
            reconnectGenerations.remove(accountId);
        } catch (Exception e) {
            log.warn("【账号{}】账号删除事件处理异常: {}", accountId, e.getMessage(), e);
        }
    }

    private void triggerWsDisconnectNotify(Long accountId) {
        try {
            // 防抖：10分钟内只发送一次
            Long lastNotifyTime = lastDisconnectNotifyTimes.get(accountId);
            long now = System.currentTimeMillis();
            if (lastNotifyTime != null && (now - lastNotifyTime) < NOTIFY_INTERVAL_MS) {
                log.debug("【账号{}】账号监听掉线通知防抖中，跳过本次发送", accountId);
                return;
            }
            lastDisconnectNotifyTimes.put(accountId, now);

            String title = "【闲鱼助手】账号连接已断开";
            String content = notificationContentBuilder.eventContent(
                    accountId,
                    "WebSocket 连接多次重连失败",
                    "消息监听连接已断开，自动回复、自动发货和在线消息都会受影响。",
                    "请检查该账号 Cookie、网络连接、验证码状态，并在连接管理中确认是否重新连上。"
            );
            notificationService.notifyEvent(NotificationService.EVENT_WS_DISCONNECT, title, content);
        } catch (Exception e) {
            log.warn("触发账号监听掉线通知异常: {}", e.getMessage());
        }
    }
}
