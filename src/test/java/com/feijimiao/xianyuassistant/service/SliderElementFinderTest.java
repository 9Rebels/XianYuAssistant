package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SliderElementFinderTest {

    @Test
    void prefersFrameFastCombinationForNcSlider() {
        SliderElementFinder finder = new SliderElementFinder(new SliderCaptchaBlockDetector());
        Page page = mock(Page.class);
        Frame frame = mock(Frame.class);
        ElementHandle button = visibleElement();
        ElementHandle track = visibleElement();
        when(page.isClosed()).thenReturn(false);
        when(page.frames()).thenReturn(List.of(frame));
        when(frame.isDetached()).thenReturn(false);
        when(frame.querySelector("#nc_1_n1z")).thenReturn(button);
        when(frame.querySelector("#nc_1_n1t")).thenReturn(track);
        when(frame.content()).thenReturn("");
        when(frame.title()).thenReturn("");
        when(frame.url()).thenReturn("");

        SliderElements elements = finder.find(page, null);

        assertNotNull(elements);
        assertEquals(button, elements.getButton());
        assertEquals(track, elements.getTrack());
        assertTrue(elements.getTarget().label().startsWith("Frame"));
    }

    @Test
    void frameSliderWinsOverMainPunishShellDuringLookup() {
        SliderElementFinder finder = new SliderElementFinder(new SliderCaptchaBlockDetector());
        Page page = mock(Page.class);
        Frame frame = mock(Frame.class);
        ElementHandle button = visibleElement();
        ElementHandle track = visibleElement();
        when(page.isClosed()).thenReturn(false);
        when(page.frames()).thenReturn(List.of(frame));
        when(page.url()).thenReturn(
                "https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha");
        when(page.title()).thenReturn("验证码拦截");
        when(page.content()).thenReturn("");
        when(frame.isDetached()).thenReturn(false);
        when(frame.querySelector("#nc_1_n1z")).thenReturn(button);
        when(frame.querySelector("#nc_1_n1t")).thenReturn(track);
        when(frame.content()).thenReturn("");
        when(frame.title()).thenReturn("");
        when(frame.url()).thenReturn("https://g.alicdn.com/sd/ncpc/nc.js");

        SliderElements elements = finder.find(page, null);

        assertNotNull(elements);
        assertFalse(elements.isBlocked());
        assertEquals(button, elements.getButton());
    }

    @Test
    void returnsBlockedWhenPunishShellHasNoOperableSlider() {
        SliderElementFinder finder = new SliderElementFinder(new SliderCaptchaBlockDetector());
        Page page = mock(Page.class);
        when(page.isClosed()).thenReturn(false);
        when(page.frames()).thenReturn(List.of());
        when(page.url()).thenReturn(
                "https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha");
        when(page.title()).thenReturn("验证码拦截");
        when(page.content()).thenReturn("");

        SliderElements elements = finder.find(page, null);

        assertNotNull(elements);
        assertTrue(elements.isBlocked());
        assertEquals("punish_captcha", elements.getBlock().getKind());
    }

    @Test
    void doesNotClickPunishShellWithoutReadySlider() {
        SliderElementFinder finder = new SliderElementFinder(new SliderCaptchaBlockDetector());
        Page page = mock(Page.class);
        Mouse mouse = mock(Mouse.class);
        ElementHandle shell = visibleElement();
        BoundingBox box = new BoundingBox();
        box.x = 10D;
        box.y = 20D;
        box.width = 100D;
        box.height = 40D;

        when(page.isClosed()).thenReturn(false);
        when(page.frames()).thenReturn(List.of());
        when(page.mouse()).thenReturn(mouse);
        when(page.url()).thenReturn(
                "https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha");
        when(page.title()).thenReturn("验证码拦截");
        when(page.content()).thenReturn("");
        when(page.querySelector(".errloading")).thenReturn(shell);
        when(shell.boundingBox()).thenReturn(box);

        SliderElements elements = finder.find(page, null);

        assertNotNull(elements);
        assertTrue(elements.isBlocked());
        verify(mouse, never()).click(60D, 40D);
    }

    private ElementHandle visibleElement() {
        ElementHandle element = mock(ElementHandle.class);
        when(element.isVisible()).thenReturn(true);
        return element;
    }
}
