package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class SliderElementFinder {
    private static final String[] CONTAINER_SELECTORS = {
            "#nc_1_n1z",
            "#baxia-dialog-content",
            ".nc-container",
            ".nc_wrapper",
            ".nc_scale",
            "[class*='nc-container']",
            "#nocaptcha",
            ".nc_1_nocaptcha",
            ".sm-pop-inner.nc-container",
            ".sm-btn-wrapper",
            ".scratch-captcha-container",
            ".scratch-captcha-question-bg",
            "[class*='slider']",
            "[class*='btn_slide']"
    };
    private static final String[] BUTTON_SELECTORS = {
            "#nc_1_n1z",
            ".nc_iconfont",
            ".btn_slide",
            ".sm-btn",
            "#scratch-captcha-btn",
            ".scratch-captcha-slider .button",
            "[class*='btn_slide']",
            "[role='button']"
    };
    private static final String[] TRACK_SELECTORS = {
            "#nc_1_n1t",
            ".nc_scale",
            ".nc_1_n1t",
            ".scratch-captcha-slider",
            "[class*='track']",
            "[class*='scale']",
            ".nc-container",
            ".nc_wrapper"
    };

    private final SliderCaptchaBlockDetector blockDetector;

    SliderElements find(Page page, SliderSearchTarget cachedTarget) {
        if (page == null || page.isClosed()) {
            return null;
        }
        List<SliderSearchTarget> targets = buildTargets(page, cachedTarget);
        SliderElements firstBlock = null;
        for (SliderSearchTarget target : targets) {
            SliderCaptchaBlock block = detectRecoverableBlock(page, target);
            if (block != null) {
                log.warn("{} 命中高风险验证码页[{}]: {}",
                        target.label(), block.getKind(), block.getMessage());
                if (firstBlock == null) {
                    firstBlock = SliderElements.blocked(target, block);
                }
            }
        }
        SliderElements fastFrameMatch = findFastFrameCombination(targets);
        if (fastFrameMatch != null) {
            return fastFrameMatch;
        }
        for (SliderSearchTarget target : targets) {
            SliderElements found = findCompleteElements(target);
            if (found != null) {
                return found;
            }
        }
        if (firstBlock != null) {
            return firstBlock;
        }
        log.warn("未找到完整滑块元素: targets={}", targets.size());
        return null;
    }

    private List<SliderSearchTarget> buildTargets(Page page, SliderSearchTarget cachedTarget) {
        List<SliderSearchTarget> targets = new ArrayList<>();
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        if (cachedTarget != null && !cachedTarget.isDetached()) {
            addTarget(targets, seen, cachedTarget);
        }
        try {
            List<Frame> frames = page.frames();
            for (int i = 0; i < frames.size(); i++) {
                Frame frame = frames.get(i);
                if (frame != null && !frame.isDetached()) {
                    addTarget(targets, seen, PlaywrightSliderSearchTarget.of(frame, i));
                }
            }
            log.info("滑块元素查找目标已准备: frames={}, totalTargets={}", frames.size(), targets.size() + 1);
        } catch (Exception e) {
            log.debug("获取滑块 frame 列表失败: {}", e.getMessage());
        }
        addTarget(targets, seen, PlaywrightSliderSearchTarget.of(page));
        return targets;
    }

    private void addTarget(List<SliderSearchTarget> targets,
                           Map<Object, Boolean> seen,
                           SliderSearchTarget target) {
        Object identity = target.identity();
        if (identity != null && seen.put(identity, Boolean.TRUE) == null) {
            targets.add(target);
        }
    }

    private SliderCaptchaBlock detectRecoverableBlock(Page page, SliderSearchTarget target) {
        SliderCaptchaBlock block = blockDetector.detect(target);
        return blockDetector.waitForPunishSliderReadyIfNeeded(
                page, target, block, target.label() + "滑块探测");
    }

    private SliderElements findFastFrameCombination(List<SliderSearchTarget> targets) {
        for (SliderSearchTarget target : targets) {
            if (!target.label().startsWith("Frame")) {
                continue;
            }
            ElementHandle button = firstVisible(target, new String[]{"#nc_1_n1z"});
            ElementHandle track = firstVisible(target, new String[]{"#nc_1_n1t"});
            if (button == null || track == null) {
                continue;
            }
            ElementHandle container = firstExisting(target, new String[]{"#baxia-dialog-content", ".nc-container"});
            if (container == null) {
                container = button;
            }
            log.info("{} 快速找到完整滑块组合: button=#nc_1_n1z, track=#nc_1_n1t",
                    target.label());
            return SliderElements.found(target, container, button, track, "#nc_1_n1z", "#nc_1_n1t");
        }
        return null;
    }

    private SliderElements findCompleteElements(SliderSearchTarget target) {
        ElementHandle container = firstVisible(target, CONTAINER_SELECTORS);
        if (container == null) {
            return null;
        }
        SelectorMatch button = firstVisibleMatch(target, BUTTON_SELECTORS);
        if (button == null) {
            return null;
        }
        SelectorMatch track = firstVisibleMatch(target, TRACK_SELECTORS);
        if (track == null) {
            return null;
        }
        log.info("{} 找到滑块元素: button={}, track={}",
                target.label(), button.selector, track.selector);
        return SliderElements.found(
                target,
                container,
                button.element,
                track.element,
                button.selector,
                track.selector
        );
    }

    private ElementHandle firstExisting(SliderSearchTarget target, String[] selectors) {
        for (String selector : selectors) {
            ElementHandle element = safeQuery(target, selector);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private ElementHandle firstVisible(SliderSearchTarget target, String[] selectors) {
        SelectorMatch match = firstVisibleMatch(target, selectors);
        return match == null ? null : match.element;
    }

    private SelectorMatch firstVisibleMatch(SliderSearchTarget target, String[] selectors) {
        for (String selector : selectors) {
            ElementHandle element = safeQuery(target, selector);
            if (element != null && isVisible(element)) {
                return new SelectorMatch(selector, element);
            }
        }
        return null;
    }

    private ElementHandle safeQuery(SliderSearchTarget target, String selector) {
        try {
            return target.querySelector(selector);
        } catch (Exception e) {
            log.debug("{} 查找滑块元素失败: selector={}, error={}",
                    target.label(), selector, e.getMessage());
            return null;
        }
    }

    private boolean isVisible(ElementHandle element) {
        try {
            return element.isVisible();
        } catch (Exception e) {
            return true;
        }
    }

    private record SelectorMatch(String selector, ElementHandle element) {
    }
}
