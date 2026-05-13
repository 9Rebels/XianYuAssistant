package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;

interface SliderSearchTarget {
    ElementHandle querySelector(String selector);

    String content();

    String title();

    String url();

    String label();

    boolean isDetached();

    Object identity();
}
