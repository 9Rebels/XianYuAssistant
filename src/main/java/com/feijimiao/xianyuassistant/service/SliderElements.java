package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import lombok.Value;

@Value
class SliderElements {
    SliderSearchTarget target;
    ElementHandle container;
    ElementHandle button;
    ElementHandle track;
    String buttonSelector;
    String trackSelector;
    SliderCaptchaBlock block;

    static SliderElements found(SliderSearchTarget target,
                                ElementHandle container,
                                ElementHandle button,
                                ElementHandle track,
                                String buttonSelector,
                                String trackSelector) {
        return new SliderElements(target, container, button, track, buttonSelector, trackSelector, null);
    }

    static SliderElements blocked(SliderSearchTarget target, SliderCaptchaBlock block) {
        return new SliderElements(target, null, null, null, null, null, block);
    }

    boolean isBlocked() {
        return block != null;
    }

    boolean hasCompleteElements() {
        return button != null && track != null;
    }
}
