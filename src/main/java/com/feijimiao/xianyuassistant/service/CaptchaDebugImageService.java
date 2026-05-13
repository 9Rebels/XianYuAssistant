package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.config.SliderProperties;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class CaptchaDebugImageService {
    private static final String DEFAULT_CAPTCHA_DEBUG_DIR = "dbdata/captcha-debug";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int SCREENSHOT_TIMEOUT_MS = 10000;

    @Autowired(required = false)
    private SliderProperties sliderProperties;

    public Optional<Path> capture(Page page, Long accountId, String reason) {
        if (page == null || page.isClosed()) {
            return Optional.empty();
        }
        try {
            Path target = resolveTargetPath(accountId, reason);
            Files.createDirectories(target.getParent());
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(target)
                    .setFullPage(true)
                    .setTimeout(SCREENSHOT_TIMEOUT_MS));
            cleanupOldImages(target.getParent());
            log.info("验证码人工处理截图已保存: accountId={}, path={}", accountId, target);
            return Optional.of(target);
        } catch (Exception e) {
            log.warn("保存验证码人工处理截图失败: accountId={}, error={}", accountId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Path> latest(Long accountId) {
        Path dir = resolveRoot().resolve(accountFolder(accountId));
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"))
                    .max(Comparator.comparingLong(this::lastModifiedMillis));
        } catch (Exception e) {
            log.debug("读取最新验证码截图失败: accountId={}, error={}", accountId, e.getMessage());
            return Optional.empty();
        }
    }

    public String latestImageUrl(Long accountId) {
        if (accountId == null) {
            return "";
        }
        long version = latest(accountId).map(this::lastModifiedMillis).orElse(System.currentTimeMillis());
        return "/api/captcha/debug-image/latest?xianyuAccountId=" + accountId + "&v=" + version;
    }

    private Path resolveTargetPath(Long accountId, String reason) {
        String safeReason = reason == null ? "manual" : reason.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        String filename = LocalDateTime.now().format(TS) + "_" + safeReason + ".png";
        return resolveRoot().resolve(accountFolder(accountId)).resolve(filename);
    }

    private Path resolveRoot() {
        String directory = DEFAULT_CAPTCHA_DEBUG_DIR;
        if (sliderProperties != null
                && sliderProperties.getFailureCapture() != null
                && sliderProperties.getFailureCapture().getDirectory() != null
                && !sliderProperties.getFailureCapture().getDirectory().isBlank()) {
            directory = sliderProperties.getFailureCapture().getDirectory();
        }
        Path root = Path.of(directory);
        return root.isAbsolute() ? root : Path.of(System.getProperty("user.dir")).resolve(root);
    }

    private void cleanupOldImages(Path dir) {
        int retain = sliderProperties == null || sliderProperties.getFailureCapture() == null
                ? 30 : sliderProperties.getFailureCapture().getRetainPerAccount();
        if (retain <= 0 || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            Path[] images = paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .toArray(Path[]::new);
            for (int i = retain; i < images.length; i++) {
                Files.deleteIfExists(images[i]);
            }
        } catch (Exception e) {
            log.debug("清理旧验证码截图失败: dir={}, error={}", dir, e.getMessage());
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private String accountFolder(Long accountId) {
        return accountId == null ? "anonymous" : String.valueOf(accountId);
    }
}
