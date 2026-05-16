package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 以独立进程方式启动 fingerprint-chromium，暴露 CDP 端口供 Playwright connectOverCDP 连接。
 * 绕过 Playwright 内部 Runtime.Enable 调用，避免 V8 debugger agent 被反爬检测。
 */
@Slf4j
class ExternalBrowserLauncher {

    private static final int BASE_PORT = 19222;
    private static final int PORT_RANGE = 100;
    private static final long STARTUP_TIMEOUT_MS = 15_000L;
    private static final long POLL_INTERVAL_MS = 300L;

    private final Process process;
    private final int debugPort;
    private final String wsEndpoint;

    private ExternalBrowserLauncher(Process process, int debugPort, String wsEndpoint) {
        this.process = process;
        this.debugPort = debugPort;
        this.wsEndpoint = wsEndpoint;
    }

    static ExternalBrowserLauncher launch(List<String> extraArgs, Path userDataDir, Long accountId) {
        int port = allocatePort(accountId);
        killExistingOnPort(port);
        List<String> cmd = buildCommand(extraArgs, userDataDir, port);
        log.info("外部启动 fingerprint-chromium: port={}, accountId={}, userDataDir={}", port, accountId, userDataDir);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            String display = System.getenv("DISPLAY");
            if (display != null && !display.isBlank()) {
                pb.environment().put("DISPLAY", display);
            }
            Process process = pb.start();
            drainOutputAsync(process);

            String wsEndpoint = waitForDevToolsEndpoint(port);
            if (wsEndpoint == null) {
                process.destroyForcibly();
                throw new RuntimeException("fingerprint-chromium 启动超时，未能获取 DevTools WebSocket 端点");
            }
            log.info("fingerprint-chromium 已启动: port={}, ws={}", port, wsEndpoint);
            return new ExternalBrowserLauncher(process, port, wsEndpoint);
        } catch (Exception e) {
            throw new RuntimeException("启动外部浏览器进程失败: " + e.getMessage(), e);
        }
    }

    String getWsEndpoint() {
        return wsEndpoint;
    }

    String getCdpUrl() {
        return "http://127.0.0.1:" + debugPort;
    }

    int getDebugPort() {
        return debugPort;
    }

    boolean isAlive() {
        return process != null && process.isAlive();
    }

    void shutdown() {
        if (process == null) return;
        try {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            log.info("外部浏览器进程已关闭: port={}", debugPort);
        } catch (Exception e) {
            log.debug("关闭外部浏览器进程异常: {}", e.getMessage());
            process.destroyForcibly();
        }
    }

    private static int allocatePort(Long accountId) {
        if (accountId == null) {
            return BASE_PORT;
        }
        return BASE_PORT + (int) (Math.abs(accountId) % PORT_RANGE);
    }

    private static void killExistingOnPort(int port) {
        try {
            ProcessBuilder pb = new ProcessBuilder("fuser", "-k", port + "/tcp");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> buildCommand(List<String> extraArgs, Path userDataDir, int port) {
        Path chromePath = PlaywrightBrowserUtils.resolveChromiumPath().orElseThrow(
                () -> new RuntimeException("fingerprint-chromium 路径未配置"));
        List<String> cmd = new ArrayList<>();
        cmd.add(chromePath.toString());
        cmd.add("--remote-debugging-port=" + port);
        cmd.add("--remote-debugging-address=127.0.0.1");
        if (userDataDir != null) {
            try {
                Files.createDirectories(userDataDir);
            } catch (Exception ignored) {}
            cmd.add("--user-data-dir=" + userDataDir.toAbsolutePath());
        }
        cmd.addAll(extraArgs);
        return cmd;
    }

    private static String waitForDevToolsEndpoint(int port) {
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            String endpoint = probeDevToolsEndpoint(port);
            if (endpoint != null) {
                return endpoint;
            }
        }
        return null;
    }

    private static String probeDevToolsEndpoint(int port) {
        try {
            URI uri = URI.create("http://127.0.0.1:" + port + "/json/version");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String json = sb.toString();
                    int idx = json.indexOf("webSocketDebuggerUrl");
                    if (idx >= 0) {
                        int start = json.indexOf("ws://", idx);
                        int end = json.indexOf("\"", start);
                        if (start >= 0 && end > start) {
                            return json.substring(start, end);
                        }
                    }
                    return "http://127.0.0.1:" + port;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void drainOutputAsync(Process process) {
        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("ERROR") || line.contains("FATAL")) {
                        log.warn("chromium stderr: {}", line);
                    } else {
                        log.debug("chromium: {}", line);
                    }
                }
            } catch (Exception ignored) {}
        }, "chromium-output-drainer");
        drainer.setDaemon(true);
        drainer.start();
    }
}
