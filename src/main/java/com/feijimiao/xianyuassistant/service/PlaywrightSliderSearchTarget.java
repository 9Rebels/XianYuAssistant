package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;

final class PlaywrightSliderSearchTarget implements SliderSearchTarget {
    private final Page page;
    private final Frame frame;
    private final String label;

    private PlaywrightSliderSearchTarget(Page page, Frame frame, String label) {
        this.page = page;
        this.frame = frame;
        this.label = label;
    }

    static PlaywrightSliderSearchTarget of(Page page) {
        return new PlaywrightSliderSearchTarget(page, null, "主页面");
    }

    static PlaywrightSliderSearchTarget of(Frame frame, int index) {
        return new PlaywrightSliderSearchTarget(null, frame, "Frame " + index);
    }

    @Override
    public ElementHandle querySelector(String selector) {
        return page != null ? page.querySelector(selector) : frame.querySelector(selector);
    }

    @Override
    public String content() {
        return page != null ? page.content() : frame.content();
    }

    @Override
    public String title() {
        return page != null ? page.title() : frame.title();
    }

    @Override
    public String url() {
        return page != null ? page.url() : frame.url();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public boolean isDetached() {
        try {
            return frame != null && frame.isDetached();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public Object identity() {
        return page != null ? page : frame;
    }
}
