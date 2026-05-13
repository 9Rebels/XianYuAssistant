package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SliderCaptchaBlockDetectorTest {

    @Test
    void punishUrlWithoutOperableSliderIsHardBlock() {
        SliderCaptchaBlockDetector detector = new SliderCaptchaBlockDetector();
        TestSliderSearchTarget target = new TestSliderSearchTarget("page")
                .withUrl("https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha")
                .withTitle("验证码拦截");

        SliderCaptchaBlock block = detector.detect(target);

        assertEquals("punish_captcha", block.getKind());
    }

    @Test
    void punishUrlWithReadySliderIsNotHardBlock() {
        SliderCaptchaBlockDetector detector = new SliderCaptchaBlockDetector();
        TestSliderSearchTarget target = new TestSliderSearchTarget("page")
                .withUrl("https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha")
                .withElement("#nc_1_n1z", visibleElement())
                .withElement("#nc_1_n1t", visibleElement());

        assertNull(detector.detect(target));
        assertTrue(detector.hasReadyPunishSliderDom(target));
    }

    @Test
    void recoverableShellClickCanClearPunishBlock() {
        SliderCaptchaBlockDetector detector = new SliderCaptchaBlockDetector();
        Page page = mock(Page.class);
        Mouse mouse = mock(Mouse.class);
        ElementHandle shell = visibleElement();
        BoundingBox box = new BoundingBox();
        box.x = 10;
        box.y = 20;
        box.width = 100;
        box.height = 40;
        when(page.mouse()).thenReturn(mouse);
        when(page.isClosed()).thenReturn(false);
        when(shell.boundingBox()).thenReturn(box);

        TestSliderSearchTarget target = new TestSliderSearchTarget("page")
                .withElement(".errloading", shell)
                .withElement("#nc_1_n1z", visibleElement())
                .withElement("#nc_1_n1t", visibleElement());

        SliderCaptchaBlock refreshed = detector.recoverPunishShellIfPossible(
                page,
                target,
                new SliderCaptchaBlock("punish_captcha", "", "", "shell"),
                "test"
        );

        assertNull(refreshed);
        verify(mouse).click(60D, 40D);
    }

    @Test
    void failureSignalFindsKeywordAndFailureElement() {
        SliderCaptchaBlockDetector detector = new SliderCaptchaBlockDetector();
        TestSliderSearchTarget keywordTarget = new TestSliderSearchTarget("page")
                .withContent("验证失败，点击框体重试");
        TestSliderSearchTarget elementTarget = new TestSliderSearchTarget("frame")
                .withElement(".errloading", visibleElement());

        assertTrue(detector.hasFailureSignal(keywordTarget));
        assertTrue(detector.hasFailureSignal(elementTarget));
        assertFalse(detector.hasFailureSignal(new TestSliderSearchTarget("plain")));
    }

    private ElementHandle visibleElement() {
        ElementHandle element = mock(ElementHandle.class);
        when(element.isVisible()).thenReturn(true);
        return element;
    }
}
