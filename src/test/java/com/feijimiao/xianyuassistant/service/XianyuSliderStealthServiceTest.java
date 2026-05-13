package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XianyuSliderStealthServiceTest {

    @Test
    void shouldUseHeadlessWhenDisplayMissing() {
        assertTrue(XianyuSliderStealthService.shouldUseHeadless(null));
        assertTrue(XianyuSliderStealthService.shouldUseHeadless(""));
        assertTrue(XianyuSliderStealthService.shouldUseHeadless("   "));
    }

    @Test
    void shouldUseHeadfulWhenDisplayExists() {
        assertFalse(XianyuSliderStealthService.shouldUseHeadless(":99"));
    }
}
