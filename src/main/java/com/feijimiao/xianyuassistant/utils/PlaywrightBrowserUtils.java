package com.feijimiao.xianyuassistant.utils;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 主链路固定走 fingerprint-chromium，不再做路径探测 / ENV 读取 / Playwright 内置 chromium 回退。
 * 部署形态：容器内由 Dockerfile 把 fingerprint-chromium AppImage 解压到 {@value #CHROMIUM_PATH_STR}。
 */
@Slf4j
public final class PlaywrightBrowserUtils {

    private static final String CHROMIUM_PATH_STR = "/opt/fingerprint-chromium/chrome-launcher";
    private static final Path CHROMIUM_PATH = Path.of(CHROMIUM_PATH_STR);
    private static final String CHROMIUM_FULL_VERSION = "144.0.7559.132";
    private static final String CHROMIUM_MAJOR_VERSION = "144";
    private static final String CHROMIUM_SOURCE = "fingerprint-chromium";
    private static final ChromiumRuntime RUNTIME = new ChromiumRuntime(
            CHROMIUM_PATH, CHROMIUM_FULL_VERSION, CHROMIUM_MAJOR_VERSION, CHROMIUM_SOURCE);

    private PlaywrightBrowserUtils() {
    }

    public static Optional<Path> resolveChromiumPath() {
        return Optional.of(CHROMIUM_PATH);
    }

    public static Optional<ChromiumRuntime> detectChromiumRuntime() {
        return Optional.of(RUNTIME);
    }

    public record ChromiumRuntime(Path executablePath, String fullVersion, String majorVersion, String source) {
    }
}
