package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class SliderResultVerifier {
    private final SliderCaptchaBlockDetector blockDetector;
    private final SliderPageInspector pageInspector;

    boolean isSuccess(Page page, SliderElements elements, SliderSearchTarget cachedTarget) {
        if (page == null || page.isClosed()) {
            return false;
        }
        SliderSearchTarget target = elements == null ? cachedTarget : elements.getTarget();
        if (hasPostSliderBlock(page, target, cachedTarget)) {
            return false;
        }
        if (hasFailureSignal(page, target, cachedTarget)) {
            return false;
        }
        double initialContainerWidth = elementWidth(elements == null ? null : elements.getContainer());
        if (!isElementVisible(elements == null ? null : elements.getContainer())
                || !isElementVisible(elements == null ? null : elements.getButton())) {
            return acceptDisappearedSliderIfRouteCleared(page, target, cachedTarget, "滑块容器已消失");
        }
        page.waitForTimeout(1200D);
        if (!isElementVisible(elements.getContainer()) || !isElementVisible(elements.getButton())) {
            return acceptDisappearedSliderIfRouteCleared(page, target, cachedTarget, "滑块容器二次检查已消失");
        }
        if (containerCollapsedAfterRetry(elements.getContainer(), initialContainerWidth)) {
            if (hasPostSliderBlock(page, target, cachedTarget) || looksLikeVerificationRoute(page)) {
                return false;
            }
            log.info("滑块容器宽度已收缩且页面脱离验证链路，判定滑块成功");
            return true;
        }
        if (hasFailureSignal(page, target, cachedTarget)) {
            return false;
        }
        page.waitForTimeout(500D);
        if (!isElementVisible(elements.getContainer()) || !isElementVisible(elements.getButton())) {
            return acceptDisappearedSliderIfRouteCleared(page, target, cachedTarget, "滑块容器最终检查已消失");
        }
        if (!looksLikeVerificationRoute(page)) {
            log.info("页面已脱离验证链路，按滑块成功处理: url={}", safeUrl(page));
            return true;
        }
        log.warn("滑块容器仍存在且页面仍处于验证链路，判定本次滑块未通过");
        return false;
    }

    private boolean acceptDisappearedSliderIfRouteCleared(Page page,
                                                          SliderSearchTarget target,
                                                          SliderSearchTarget cachedTarget,
                                                          String scene) {
        if (hasPostSliderBlock(page, target, cachedTarget)) {
            return false;
        }
        if (looksLikeVerificationRoute(page)) {
            log.info("{}但页面仍在验证链路，暂不判定成功: url={}, title={}",
                    scene, safeUrl(page), safeTitle(page));
            return false;
        }
        log.info("{}且页面脱离验证链路，判定滑块成功", scene);
        return true;
    }

    boolean hasPostSliderBlock(Page page, SliderSearchTarget primaryTarget, SliderSearchTarget cachedTarget) {
        return findPostSliderBlock(page, primaryTarget, cachedTarget) != null;
    }

    SliderCaptchaBlock findPostSliderBlock(Page page, SliderSearchTarget primaryTarget, SliderSearchTarget cachedTarget) {
        for (SliderSearchTarget target : resultTargets(page, primaryTarget, cachedTarget)) {
            SliderCaptchaBlock block = blockDetector.detect(target);
            if (block != null) {
                log.warn("{} 滑块后命中高风险验证码页[{}]: {}",
                        target.label(), block.getKind(), block.getMessage());
                return block;
            }
        }
        return null;
    }

    boolean hasFailureSignal(Page page, SliderSearchTarget primaryTarget, SliderSearchTarget cachedTarget) {
        for (SliderSearchTarget target : resultTargets(page, primaryTarget, cachedTarget)) {
            if (blockDetector.hasFailureSignal(target)) {
                return true;
            }
        }
        return false;
    }

    boolean looksLikeVerificationRoute(Page page) {
        SliderPageInspector.PageState state = SliderPageInspector.PageState.builder()
                .url(safeUrl(page))
                .title(safeTitle(page))
                .bodyText(safeBody(page))
                .visibleSlider(false)
                .failureSignal(false)
                .build();
        return pageInspector.looksLikeVerificationUrl(state.getUrl())
                || pageInspector.looksLikeVerificationTitle(state.getTitle())
                || pageInspector.looksLikeVerificationText(state.getBodyText());
    }

    private List<SliderSearchTarget> resultTargets(Page page,
                                                   SliderSearchTarget primaryTarget,
                                                   SliderSearchTarget cachedTarget) {
        List<SliderSearchTarget> targets = new ArrayList<>();
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        addIfValid(targets, seen, primaryTarget);
        addIfValid(targets, seen, cachedTarget);
        if (page != null && !page.isClosed()) {
            addIfValid(targets, seen, PlaywrightSliderSearchTarget.of(page));
        }
        return targets;
    }

    private void addIfValid(List<SliderSearchTarget> targets,
                            Map<Object, Boolean> seen,
                            SliderSearchTarget target) {
        if (target == null || target.isDetached()) {
            return;
        }
        Object identity = target.identity();
        if (identity != null && seen.put(identity, Boolean.TRUE) == null) {
            targets.add(target);
        }
    }

    private boolean isElementVisible(ElementHandle element) {
        if (element == null) {
            return false;
        }
        try {
            return element.isVisible();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (message.contains("detached") || message.contains("disconnected")) {
                return false;
            }
            return true;
        }
    }

    private boolean containerCollapsedAfterRetry(ElementHandle container, double initialWidth) {
        double currentWidth = elementWidth(container);
        return initialWidth > 0D && currentWidth >= 0D && currentWidth < initialWidth * 0.5D;
    }

    private double elementWidth(ElementHandle element) {
        if (element == null) {
            return -1D;
        }
        try {
            BoundingBox box = element.boundingBox();
            return box == null ? -1D : Math.max(0D, box.width);
        } catch (Exception e) {
            return -1D;
        }
    }

    private String safeUrl(Page page) {
        try {
            String url = page.url();
            return url == null ? "" : url;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeTitle(Page page) {
        try {
            String title = page.title();
            return title == null ? "" : title;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeBody(Page page) {
        try {
            String body = page.innerText("body", new Page.InnerTextOptions().setTimeout(1500));
            return body == null ? "" : body;
        } catch (Exception e) {
            try {
                String body = page.content();
                return body == null ? "" : body;
            } catch (Exception ignored) {
                return "";
            }
        }
    }
}
