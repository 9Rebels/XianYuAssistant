package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.config.SliderProperties;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@Slf4j
@Service
public class SliderAutoVerifyService {
    private static final int MAX_RETRIES = 5;
    private static final double MIN_DISTANCE = 40D;
    private static final double RETRY_2_MIN_DELAY_MS = 12000D;
    private static final double RETRY_2_MAX_DELAY_MS = 18000D;
    private static final double RETRY_3_MIN_DELAY_MS = 18000D;
    private static final double RETRY_3_MAX_DELAY_MS = 25000D;
    private static final double LATE_RETRY_MIN_DELAY_MS = 25000D;
    private static final double LATE_RETRY_MAX_DELAY_MS = 35000D;
    private static final int FAILURE_SCREENSHOT_TIMEOUT_MS = 5000;
    private static final DateTimeFormatter SCREENSHOT_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final ExecutorService FAILURE_CAPTURE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "slider-failure-capture");
        thread.setDaemon(true);
        return thread;
    });

    private final SliderCaptchaBlockDetector blockDetector;
    private final SliderElementFinder elementFinder;
    private final SliderResultVerifier resultVerifier;
    private final SliderTrajectoryPlanner trajectoryPlanner;
    private SliderSearchTarget cachedSliderTarget;
    private String lastPlanProfile;
    private double lastPlanOvershoot;
    private double lastPlanDelay;
    private double lastPlanCurve;
    private double lastPlanYJitter;
    private int lastPlanSteps;

    private SliderProperties sliderProperties;
    private SliderTrajectoryLearner trajectoryLearner;

    public SliderAutoVerifyService() {
        this(new SliderCaptchaBlockDetector(), new SliderPageInspector());
    }

    SliderAutoVerifyService(SliderCaptchaBlockDetector blockDetector, SliderPageInspector pageInspector) {
        this.blockDetector = blockDetector;
        this.elementFinder = new SliderElementFinder(blockDetector);
        this.resultVerifier = new SliderResultVerifier(blockDetector, pageInspector);
        this.trajectoryPlanner = new SliderTrajectoryPlanner();
    }

    @Autowired(required = false)
    void setSliderProperties(SliderProperties sliderProperties) {
        this.sliderProperties = sliderProperties;
    }

    @Autowired(required = false)
    void setTrajectoryLearner(SliderTrajectoryLearner trajectoryLearner) {
        this.trajectoryLearner = trajectoryLearner;
    }

    public boolean solve(Page page) {
        return solve(page, null);
    }

    public boolean solve(Page page, Long accountId) {
        return solveDetailed(page, accountId).isSuccess();
    }

    SliderAutoVerifyResult solveDetailed(Page page, Long accountId) {
        if (page == null || page.isClosed()) {
            return SliderAutoVerifyResult.failed("page_closed");
        }
        if (trajectoryLearner != null && accountId != null) {
            trajectoryPlanner.setLearner(trajectoryLearner, accountId);
        }
        cachedSliderTarget = null;
        warmup(page);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 1) {
                SliderCaptchaBlock preRetryBlock = detectCachedTargetBlock();
                if (preRetryBlock != null) {
                    captureFailure(page, accountId, attempt, "post_slider_block");
                    return SliderAutoVerifyResult.hardBlock(preRetryBlock);
                }
                waitBeforeRetry(page, attempt);
            }
            SliderElements elements = elementFinder.find(page, cachedSliderTarget);
            if (elements == null) {
                log.warn("自动滑块未找到可操作元素: attempt={}", attempt);
                cachedSliderTarget = null;
                captureFailure(page, accountId, attempt, "no_elements");
                return SliderAutoVerifyResult.failed("no_elements");
            }
            if (elements.isBlocked()) {
                log.warn("自动滑块命中硬拦截: kind={}, message={}",
                        elements.getBlock().getKind(), elements.getBlock().getMessage());
                captureFailure(page, accountId, attempt,
                        "blocked_" + elements.getBlock().getKind());
                return SliderAutoVerifyResult.hardBlock(elements.getBlock());
            }
            cachedSliderTarget = elements.getTarget();
            if (!dragSliderOnce(page, elements, attempt)) {
                captureFailure(page, accountId, attempt, "drag_failed");
                continue;
            }
            if (resultVerifier.isSuccess(page, elements, cachedSliderTarget)) {
                log.info("自动滑块验证成功: attempt={}", attempt);
                trajectoryPlanner.recordResult(lastPlanProfile, lastPlanOvershoot, lastPlanDelay, lastPlanCurve, lastPlanYJitter, lastPlanSteps, true);
                return SliderAutoVerifyResult.success();
            }
            log.warn("自动滑块第{}次尝试未通过，准备重试", attempt);
            trajectoryPlanner.recordResult(lastPlanProfile, lastPlanOvershoot, lastPlanDelay, lastPlanCurve, lastPlanYJitter, lastPlanSteps, false);
            captureFailure(page, accountId, attempt, "verify_failed");
        }
        return SliderAutoVerifyResult.failed("verify_failed");
    }

    private SliderCaptchaBlock detectCachedTargetBlock() {
        if (cachedSliderTarget == null || cachedSliderTarget.isDetached()) {
            return null;
        }
        SliderCaptchaBlock block = blockDetector.detect(cachedSliderTarget);
        if (block != null) {
            log.warn("{} 重试前命中高风险验证码页[{}]: {}",
                    cachedSliderTarget.label(), block.getKind(), block.getMessage());
        }
        return block;
    }

    private boolean dragSliderOnce(Page page, SliderElements elements, int attempt) {
        double distance = calculateSlideDistance(elements);
        if (distance < MIN_DISTANCE) {
            log.warn("自动滑块距离异常: target={}, button={}, track={}, distance={}, attempt={}",
                    elements.getTarget().label(),
                    elements.getButtonSelector(),
                    elements.getTrackSelector(),
                    distance,
                    attempt);
            return false;
        }
        log.info("自动滑块准备拖动: target={}, button={}, track={}, distance={}, attempt={}",
                elements.getTarget().label(),
                elements.getButtonSelector(),
                elements.getTrackSelector(),
                Math.round(distance * 10D) / 10D,
                attempt);
        SliderTrajectoryPlanner.TrajectoryPlan plan = trajectoryPlanner.plan(distance, attempt);
        lastPlanProfile = plan.getProfileName();
        lastPlanOvershoot = plan.getOvershootRatio();
        lastPlanDelay = plan.getBaseDelayMs();
        lastPlanCurve = plan.getAccelerationCurve();
        lastPlanYJitter = 0;
        lastPlanSteps = plan.getPoints().size();
        return simulateSlide(page, elements.getButton(), plan, attempt);
    }

    private void warmup(Page page) {
        try {
            page.waitForTimeout(randomBetween(1000, 1800));
            page.mouse().move(randomBetween(260, 520), randomBetween(180, 360),
                    new com.microsoft.playwright.Mouse.MoveOptions().setSteps(randomInt(8, 16)));
            page.waitForTimeout(randomBetween(180, 420));
            page.mouse().move(randomBetween(760, 1080), randomBetween(320, 620),
                    new com.microsoft.playwright.Mouse.MoveOptions().setSteps(randomInt(12, 24)));
            page.waitForTimeout(randomBetween(240, 620));
        } catch (Exception e) {
            log.debug("验证码页预热失败，继续滑块处理: {}", e.getMessage());
        }
    }

    private double calculateSlideDistance(SliderElements elements) {
        try {
            BoundingBox buttonBox = elements.getButton().boundingBox();
            BoundingBox trackBox = elements.getTrack().boundingBox();
            if (buttonBox == null || trackBox == null) {
                return 0D;
            }
            double distance = trackBox.width - buttonBox.width + randomBetween(-0.5, 0.5);
            log.info("计算滑动距离: distance={}, trackWidth={}, buttonWidth={}",
                    Math.round(distance * 10D) / 10D,
                    Math.round(trackBox.width * 10D) / 10D,
                    Math.round(buttonBox.width * 10D) / 10D);
            return distance;
        } catch (Exception e) {
            log.warn("计算滑块距离失败: {}", e.getMessage());
            return 0D;
        }
    }

    private boolean simulateSlide(Page page,
                                  ElementHandle button,
                                  SliderTrajectoryPlanner.TrajectoryPlan plan,
                                  int attempt) {
        try {
            double tempoSeed = randomBetween(0D, 1000D);
            double tempoBase = randomBetween(0.92D, 1.10D);
            page.waitForTimeout(randomBetween(120, 240) * tempo(tempoSeed, tempoBase, 0));
            BoundingBox buttonBox = button.boundingBox();
            if (buttonBox == null) {
                return false;
            }
            double startX = buttonBox.x + buttonBox.width / 2;
            double startY = buttonBox.y + buttonBox.height / 2;

            XdotoolMouseDriver xdotool = XdotoolMouseDriver.create(page);
            if (xdotool != null) {
                return simulateSlideWithXdotool(page, xdotool, startX, startY, button, plan, attempt, tempoSeed, tempoBase);
            }

            page.mouse().move(startX + randomBetween(-25, -20), startY + randomBetween(12, 18),
                    new com.microsoft.playwright.Mouse.MoveOptions().setSteps(randomInt(8, 10)));
            page.waitForTimeout(randomBetween(50, 150) * tempo(tempoSeed, tempoBase, 1));
            page.mouse().move(startX, startY,
                    new com.microsoft.playwright.Mouse.MoveOptions().setSteps(randomInt(8, 10)));
            page.waitForTimeout(randomBetween(70, 120) * tempo(tempoSeed, tempoBase, 2));
            hoverButton(button, attempt);
            PointerOrigin origin = recalibrateStartPoint(startX, startY, button);
            startX = origin.x();
            startY = origin.y();
            page.mouse().move(startX, startY);
            page.waitForTimeout(randomBetween(100, 150) * tempo(tempoSeed, tempoBase, 4));
            page.mouse().down();
            page.waitForTimeout(randomBetween(100, 150) * tempo(tempoSeed, tempoBase, 5));
            moveTrajectory(page, startX, startY, plan.getPoints());
            logFinalSliderPosition(button);
            page.waitForTimeout(randomBetween(10, 70) * tempo(tempoSeed, tempoBase, 6));
            page.mouse().up();
            page.waitForTimeout(randomBetween(80, 200) * tempo(tempoSeed, tempoBase, 7));
            page.waitForTimeout(serverJudgeWait(attempt) * Math.max(1D, Math.min(1.2D, tempo(tempoSeed, tempoBase, 8))));
            return true;
        } catch (Exception e) {
            log.warn("模拟滑块拖动失败: {}", e.getMessage());
            try {
                page.mouse().up();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private boolean simulateSlideWithXdotool(Page page,
                                             XdotoolMouseDriver xdotool,
                                             double startX,
                                             double startY,
                                             ElementHandle button,
                                             SliderTrajectoryPlanner.TrajectoryPlan plan,
                                             int attempt,
                                             double tempoSeed,
                                             double tempoBase) {
        log.info("使用xdotool OS级输入模拟滑块拖动");
        xdotool.moveTo(startX + randomBetween(-20, -10), startY + randomBetween(8, 15));
        xdotool.sleepMs((long) randomBetween(80, 180));
        xdotool.moveTo(startX, startY);
        xdotool.sleepMs((long) randomBetween(100, 200));
        PointerOrigin origin = recalibrateStartPoint(startX, startY, button);
        startX = origin.x();
        startY = origin.y();
        xdotool.moveTo(startX, startY);
        xdotool.sleepMs((long) randomBetween(80, 150));
        xdotool.mouseDown();
        xdotool.sleepMs((long) randomBetween(80, 150));
        moveTrajectoryXdotool(xdotool, startX, startY, plan.getPoints());
        logFinalSliderPosition(button);
        xdotool.sleepMs((long) randomBetween(20, 80));
        xdotool.mouseUp();
        page.waitForTimeout(serverJudgeWait(attempt) * Math.max(1D, Math.min(1.2D, tempo(tempoSeed, tempoBase, 8))));
        return true;
    }

    private void moveTrajectoryXdotool(XdotoolMouseDriver xdotool,
                                       double startX,
                                       double startY,
                                       List<SliderTrajectoryPlanner.TrajectoryPoint> trajectory) {
        int total = trajectory.size();
        for (int i = 0; i < total; i++) {
            SliderTrajectoryPlanner.TrajectoryPoint point = trajectory.get(i);
            xdotool.moveTo(startX + point.getX(), startY + point.getY());
            long delay = Math.round(point.getDelayMs());
            if (delay > 0) {
                xdotool.sleepMs(delay);
            }
            if (shouldHesitate(i, total)) {
                xdotool.sleepMs((long) randomBetween(20D, 60D));
            }
        }
    }

    private double tempo(double seed, double base, int phase) {
        double noise = SliderTrajectoryPlanner.perlinNoise1d(phase * 0.8D, seed);
        return Math.max(0.65D, Math.min(1.40D, base + noise * 0.15D));
    }

    private void hoverButton(ElementHandle button, int attempt) {
        boolean skipHover = attempt > 1 && ThreadLocalRandom.current().nextDouble() < 0.15;
        if (skipHover) {
            return;
        }
        try {
            button.hover(new ElementHandle.HoverOptions().setTimeout(2000));
            pageWaitAfterHover(button, attempt);
        } catch (Exception e) {
            log.debug("悬停滑块失败，继续拖动: {}", e.getMessage());
        }
    }

    private void pageWaitAfterHover(ElementHandle button, int attempt) {
        try {
            Page owner = button.ownerFrame() == null ? null : button.ownerFrame().page();
            if (owner != null) {
                owner.waitForTimeout(attempt == 1 ? randomBetween(80, 330) : randomBetween(50, 220));
            }
        } catch (Exception ignored) {
        }
    }

    private double serverJudgeWait(int attempt) {
        if (attempt == 1) {
            return randomBetween(2200, 4200);
        }
        return randomBetween(2000, 3600);
    }

    private void logFinalSliderPosition(ElementHandle button) {
        try {
            String style = button.getAttribute("style");
            if (style != null && style.contains("left")) {
                log.info("滑动完成，按钮 style={}", style);
            }
        } catch (Exception ignored) {
        }
    }

    private void moveTrajectory(Page page,
                                double startX,
                                double startY,
                                List<SliderTrajectoryPlanner.TrajectoryPoint> trajectory) {
        double lastX = 0D;
        double lastY = 0D;
        int total = trajectory.size();
        for (int i = 0; i < total; i++) {
            SliderTrajectoryPlanner.TrajectoryPoint point = trajectory.get(i);
            moveToPoint(page, startX, startY, lastX, lastY, point);
            lastX = point.getX();
            lastY = point.getY();
            double delay = point.getDelayMs();
            if (delay > 1D) {
                page.waitForTimeout(delay);
            }
            if (shouldHesitate(i, total)) {
                page.waitForTimeout(randomBetween(20D, 60D));
            }
        }
    }

    private PointerOrigin recalibrateStartPoint(double fallbackX, double fallbackY, ElementHandle button) {
        try {
            BoundingBox box = button.boundingBox();
            if (box == null) {
                return new PointerOrigin(fallbackX, fallbackY);
            }
            return new PointerOrigin(box.x + box.width / 2D, box.y + box.height / 2D);
        } catch (Exception e) {
            log.debug("滑块悬停后重校准坐标失败，沿用原始坐标: {}", e.getMessage());
            return new PointerOrigin(fallbackX, fallbackY);
        }
    }

    private boolean shouldHesitate(int pointIndex, int totalPoints) {
        return isHesitationWindow(pointIndex, totalPoints)
                && ThreadLocalRandom.current().nextDouble() < 0.08D;
    }

    private boolean isHesitationWindow(int pointIndex, int totalPoints) {
        if (totalPoints <= 0) {
            return false;
        }
        double progress = (double) pointIndex / totalPoints;
        return progress > 0.15D && progress < 0.85D;
    }

    private void moveToPoint(Page page, double startX, double startY,
                             double lastX,
                             double lastY,
                             SliderTrajectoryPlanner.TrajectoryPoint point) {
        double dx = point.getX() - lastX;
        double dy = point.getY() - lastY;
        double segmentDistance = Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(2, (int) Math.round(segmentDistance / randomBetween(2.5D, 4.5D)));
        steps = Math.min(steps, 12);
        page.mouse().move(startX + point.getX(), startY + point.getY(),
                new com.microsoft.playwright.Mouse.MoveOptions().setSteps(steps));
    }

    private void waitBeforeRetry(Page page, int attempt) {
        double retryDelay = retryDelay(attempt);
        log.info("自动滑块等待后重试: attempt={}, delayMs={}", attempt, Math.round(retryDelay));
        page.waitForTimeout(retryDelay);
        if (clickToResetSlider(page)) {
            cachedSliderTarget = null;
            page.waitForTimeout(randomBetween(900, 1300));
            return;
        }
        try {
            page.reload(new Page.ReloadOptions().setTimeout(15000));
            page.waitForTimeout(randomBetween(900, 1500));
            cachedSliderTarget = null;
        } catch (Exception e) {
            log.debug("自动滑块重试 reload 失败: {}", e.getMessage());
        }
    }

    private double retryDelay(int attempt) {
        if (attempt == 2) {
            return randomBetween(RETRY_2_MIN_DELAY_MS, RETRY_2_MAX_DELAY_MS);
        }
        if (attempt == 3) {
            return randomBetween(RETRY_3_MIN_DELAY_MS, RETRY_3_MAX_DELAY_MS);
        }
        return randomBetween(LATE_RETRY_MIN_DELAY_MS, LATE_RETRY_MAX_DELAY_MS);
    }

    private boolean clickToResetSlider(Page page) {
        List<SliderSearchTarget> targets = new ArrayList<>();
        if (cachedSliderTarget != null && !cachedSliderTarget.isDetached()) {
            targets.add(cachedSliderTarget);
        }
        targets.add(PlaywrightSliderSearchTarget.of(page));
        for (SliderSearchTarget target : targets) {
            SliderCaptchaBlock block = blockDetector.detect(target);
            if (block != null) {
                log.info("自动滑块重试跳过硬拦截重置: target={}, kind={}", target.label(), block.getKind());
                return false;
            }
            if (blockDetector.clickFailureRetryIfPresent(page, target, "自动滑块重试")) {
                cachedSliderTarget = null;
                log.info("自动滑块重试已点击普通失败重试区域: target={}", target.label());
                return true;
            }
        }
        return false;
    }

    private static int randomInt(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    private static double randomBetween(double minInclusive, double maxExclusive) {
        return ThreadLocalRandom.current().nextDouble(minInclusive, maxExclusive);
    }

    private void captureFailure(Page page, Long accountId, int attempt, String reason) {
        SliderProperties.FailureCapture config = failureCaptureConfig();
        if (config == null || !config.isEnabled()) {
            return;
        }
        if (page == null || page.isClosed()) {
            return;
        }
        try {
            Path target = resolveScreenshotPath(config, accountId, attempt, reason);
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setTimeout(FAILURE_SCREENSHOT_TIMEOUT_MS));
            FAILURE_CAPTURE_EXECUTOR.submit(() -> archiveFailureScreenshot(
                    target, screenshot, config.getRetainPerAccount(), accountId, attempt, reason));
        } catch (Exception e) {
            log.debug("捕获自动滑块失败截图异常: accountId={}, attempt={}, reason={}, error={}",
                    accountId, attempt, reason, e.getMessage());
        }
    }

    private void archiveFailureScreenshot(Path target,
                                          byte[] screenshot,
                                          int retainPerAccount,
                                          Long accountId,
                                          int attempt,
                                          String reason) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, screenshot);
            log.warn("自动滑块失败截图已保存: accountId={}, attempt={}, reason={}, path={}",
                    accountId, attempt, reason, target);
            cleanupOldScreenshots(target.getParent(), retainPerAccount);
        } catch (Exception e) {
            log.debug("保存自动滑块失败截图异常: accountId={}, attempt={}, reason={}, error={}",
                    accountId, attempt, reason, e.getMessage());
        }
    }

    private SliderProperties.FailureCapture failureCaptureConfig() {
        return sliderProperties == null ? new SliderProperties().getFailureCapture()
                : sliderProperties.getFailureCapture();
    }

    private Path resolveScreenshotPath(SliderProperties.FailureCapture config,
                                       Long accountId,
                                       int attempt,
                                       String reason) {
        Path root = Path.of(config.getDirectory());
        if (!root.isAbsolute()) {
            root = Path.of(System.getProperty("user.dir")).resolve(root);
        }
        String accountFolder = accountId == null ? "anonymous" : accountId.toString();
        String safeReason = reason == null ? "unknown" : reason.replaceAll("[^a-zA-Z0-9_]", "_");
        String filename = LocalDateTime.now().format(SCREENSHOT_TS)
                + "_attempt" + attempt + "_" + safeReason + ".png";
        return root.resolve(accountFolder).resolve(filename);
    }

    private void cleanupOldScreenshots(Path directory, int retainPerAccount) {
        if (retainPerAccount <= 0 || directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> sorted = files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> safeLastModified(p)))
                    .toList();
            int removeCount = sorted.size() - retainPerAccount;
            for (int i = 0; i < removeCount; i++) {
                try {
                    Files.deleteIfExists(sorted.get(i));
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    record PointerOrigin(double x, double y) {
    }

}
