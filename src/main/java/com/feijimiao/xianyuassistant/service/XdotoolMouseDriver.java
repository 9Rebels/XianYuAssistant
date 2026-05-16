package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
class XdotoolMouseDriver {
    private static final boolean AVAILABLE = checkAvailable();
    private final int offsetX;
    private final int offsetY;
    private final String display;
    private final long windowId;

    private XdotoolMouseDriver(int offsetX, int offsetY, String display, long windowId) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.display = display;
        this.windowId = windowId;
    }

    static XdotoolMouseDriver create(Page page) {
        if (!AVAILABLE) {
            return null;
        }
        String display = System.getenv("DISPLAY");
        if (display == null || display.isBlank()) {
            return null;
        }
        try {
            long wid = findAndFocusChromiumWindow(display);
            if (wid == 0) {
                log.warn("xdotool未找到chromium窗口，回退CDP");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) page.evaluate(
                    "() => ({screenX: window.screenX, screenY: window.screenY, " +
                            "outerH: window.outerHeight, innerH: window.innerHeight})");
            int screenX = ((Number) metrics.get("screenX")).intValue();
            int screenY = ((Number) metrics.get("screenY")).intValue();
            int outerH = ((Number) metrics.get("outerH")).intValue();
            int innerH = ((Number) metrics.get("innerH")).intValue();
            int chromeHeight = outerH - innerH;
            // --window 模式下坐标相对于窗口左上角，不需要 screenX/screenY
            int offsetX = 0;
            int offsetY = chromeHeight;
            log.info("xdotool坐标映射(窗口相对): screenX={}, screenY={}, chromeHeight={}, offsetX={}, offsetY={}, windowId={}",
                    screenX, screenY, chromeHeight, offsetX, offsetY, wid);
            return new XdotoolMouseDriver(offsetX, offsetY, display, wid);
        } catch (Exception e) {
            log.warn("xdotool坐标映射失败，回退CDP: {}", e.getMessage());
            return null;
        }
    }

    void moveTo(double viewportX, double viewportY) {
        int screenX = (int) Math.round(viewportX) + offsetX;
        int screenY = (int) Math.round(viewportY) + offsetY;
        exec("mousemove", "--window", String.valueOf(windowId), "--", String.valueOf(screenX), String.valueOf(screenY));
    }

    void mouseDown() {
        exec("mousedown", "--window", String.valueOf(windowId), "1");
    }

    void mouseUp() {
        exec("mouseup", "--window", String.valueOf(windowId), "1");
    }

    void sleepMs(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void exec(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "xdotool";
            System.arraycopy(args, 0, cmd, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("DISPLAY", display);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("xdotool exec failed: {}", e.getMessage());
        }
    }

    private static long findAndFocusChromiumWindow(String display) {
        try {
            ProcessBuilder pb = new ProcessBuilder("xdotool", "search", "--onlyvisible", "--class", "chromium");
            pb.environment().put("DISPLAY", display);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor(3, TimeUnit.SECONDS);
            if (line == null || line.isBlank()) {
                pb = new ProcessBuilder("xdotool", "search", "--class", "Chromium");
                pb.environment().put("DISPLAY", display);
                pb.redirectErrorStream(true);
                p = pb.start();
                reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                line = reader.readLine();
                p.waitFor(3, TimeUnit.SECONDS);
            }
            if (line == null || line.isBlank()) {
                pb = new ProcessBuilder("xdotool", "search", "--name", "");
                pb.environment().put("DISPLAY", display);
                pb.redirectErrorStream(true);
                p = pb.start();
                reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                line = reader.readLine();
                p.waitFor(3, TimeUnit.SECONDS);
            }
            if (line != null && !line.isBlank()) {
                long wid = Long.parseLong(line.trim());
                new ProcessBuilder("xdotool", "windowactivate", "--sync", String.valueOf(wid))
                        .environment().put("DISPLAY", display);
                ProcessBuilder activatePb = new ProcessBuilder("xdotool", "windowactivate", "--sync", String.valueOf(wid));
                activatePb.environment().put("DISPLAY", display);
                activatePb.redirectErrorStream(true);
                Process activateP = activatePb.start();
                activateP.waitFor(3, TimeUnit.SECONDS);
                new ProcessBuilder("xdotool", "windowfocus", "--sync", String.valueOf(wid));
                ProcessBuilder focusPb = new ProcessBuilder("xdotool", "windowfocus", "--sync", String.valueOf(wid));
                focusPb.environment().put("DISPLAY", display);
                focusPb.redirectErrorStream(true);
                Process focusP = focusPb.start();
                focusP.waitFor(3, TimeUnit.SECONDS);
                log.info("xdotool已聚焦chromium窗口: wid={}", wid);
                return wid;
            }
        } catch (Exception e) {
            log.warn("xdotool查找chromium窗口失败: {}", e.getMessage());
        }
        return 0;
    }

    private static boolean checkAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("xdotool", "version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String version = reader.readLine();
                log.info("xdotool可用: {}", version);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }
}