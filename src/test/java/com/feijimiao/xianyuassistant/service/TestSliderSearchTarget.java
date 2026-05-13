package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;

import java.util.HashMap;
import java.util.Map;

class TestSliderSearchTarget implements SliderSearchTarget {
    private final String label;
    private final Map<String, ElementHandle> elements = new HashMap<>();
    private String content = "";
    private String title = "";
    private String url = "";
    private boolean detached;

    TestSliderSearchTarget(String label) {
        this.label = label;
    }

    TestSliderSearchTarget withElement(String selector, ElementHandle element) {
        elements.put(selector, element);
        return this;
    }

    TestSliderSearchTarget withContent(String content) {
        this.content = content;
        return this;
    }

    TestSliderSearchTarget withTitle(String title) {
        this.title = title;
        return this;
    }

    TestSliderSearchTarget withUrl(String url) {
        this.url = url;
        return this;
    }

    TestSliderSearchTarget detached() {
        this.detached = true;
        return this;
    }

    @Override
    public ElementHandle querySelector(String selector) {
        return elements.get(selector);
    }

    @Override
    public String content() {
        return content;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public boolean isDetached() {
        return detached;
    }

    @Override
    public Object identity() {
        return this;
    }
}
