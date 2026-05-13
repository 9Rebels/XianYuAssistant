package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SliderResultVerifierTest {

    @Test
    void disappearedSliderOnPunishUrlIsNotSuccess() {
        SliderResultVerifier verifier = new SliderResultVerifier(
                new SliderCaptchaBlockDetector(),
                new SliderPageInspector()
        );
        Page page = page("https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha",
                "验证码拦截",
                "");
        SliderElements elements = SliderElements.found(
                new TestSliderSearchTarget("page"),
                hiddenElement(),
                hiddenElement(),
                hiddenElement(),
                "#nc_1_n1z",
                "#nc_1_n1t"
        );

        assertFalse(verifier.isSuccess(page, elements, elements.getTarget()));
    }

    @Test
    void disappearedSliderAfterLeavingVerificationRouteIsSuccess() {
        SliderResultVerifier verifier = new SliderResultVerifier(
                new SliderCaptchaBlockDetector(),
                new SliderPageInspector()
        );
        Page page = page("https://www.goofish.com/im", "闲鱼", "");
        SliderElements elements = SliderElements.found(
                new TestSliderSearchTarget("page"),
                hiddenElement(),
                hiddenElement(),
                hiddenElement(),
                "#nc_1_n1z",
                "#nc_1_n1t"
        );

        assertTrue(verifier.isSuccess(page, elements, elements.getTarget()));
    }

    @Test
    void visibleContainerWithFailureSignalIsNotSuccess() {
        SliderResultVerifier verifier = new SliderResultVerifier(
                new SliderCaptchaBlockDetector(),
                new SliderPageInspector()
        );
        Page page = page("https://www.goofish.com/im", "闲鱼", "验证失败，点击框体重试");
        TestSliderSearchTarget target = new TestSliderSearchTarget("page")
                .withContent("验证失败，点击框体重试");
        SliderElements elements = SliderElements.found(
                target,
                visibleElement(),
                visibleElement(),
                visibleElement(),
                "#nc_1_n1z",
                "#nc_1_n1t"
        );

        assertFalse(verifier.isSuccess(page, elements, target));
    }

    @Test
    void detachedSliderAfterLeavingVerificationRouteIsSuccess() {
        SliderResultVerifier verifier = new SliderResultVerifier(
                new SliderCaptchaBlockDetector(),
                new SliderPageInspector()
        );
        Page page = page("https://www.goofish.com/im", "闲鱼", "");
        SliderElements elements = SliderElements.found(
                new TestSliderSearchTarget("frame").detached(),
                detachedElement(),
                detachedElement(),
                detachedElement(),
                "#nc_1_n1z",
                "#nc_1_n1t"
        );

        assertTrue(verifier.isSuccess(page, elements, elements.getTarget()));
    }

    @Test
    void containerWidthShrinkAfterRetryIsSoftSuccessWhenRouteLeavesVerification() {
        SliderResultVerifier verifier = new SliderResultVerifier(
                new SliderCaptchaBlockDetector(),
                new SliderPageInspector()
        );
        Page page = page("https://www.goofish.com/im", "闲鱼", "");
        SliderElements elements = SliderElements.found(
                new TestSliderSearchTarget("page"),
                widthElement(320D, 0D),
                visibleElement(),
                visibleElement(),
                "#nc_1_n1z",
                "#nc_1_n1t"
        );

        assertTrue(verifier.isSuccess(page, elements, elements.getTarget()));
    }

    @Test
    void containerWidthUnchangedOnVerificationRouteIsNotSuccess() {
        SliderResultVerifier verifier = new SliderResultVerifier(
                new SliderCaptchaBlockDetector(),
                new SliderPageInspector()
        );
        Page page = page("https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc", "验证码拦截", "");
        SliderElements elements = SliderElements.found(
                new TestSliderSearchTarget("page"),
                widthElement(320D, 320D),
                visibleElement(),
                visibleElement(),
                "#nc_1_n1z",
                "#nc_1_n1t"
        );

        assertFalse(verifier.isSuccess(page, elements, elements.getTarget()));
    }

    private Page page(String url, String title, String content) {
        Page page = mock(Page.class);
        when(page.isClosed()).thenReturn(false);
        when(page.url()).thenReturn(url);
        when(page.title()).thenReturn(title);
        when(page.content()).thenReturn(content);
        return page;
    }

    private ElementHandle visibleElement() {
        ElementHandle element = mock(ElementHandle.class);
        when(element.isVisible()).thenReturn(true);
        return element;
    }

    private ElementHandle hiddenElement() {
        ElementHandle element = mock(ElementHandle.class);
        when(element.isVisible()).thenReturn(false);
        return element;
    }

    private ElementHandle detachedElement() {
        ElementHandle element = mock(ElementHandle.class);
        when(element.isVisible()).thenThrow(new RuntimeException("Element is detached from document"));
        return element;
    }

    private ElementHandle widthElement(double firstWidth, double secondWidth) {
        ElementHandle element = mock(ElementHandle.class);
        BoundingBox first = box(firstWidth);
        BoundingBox second = box(secondWidth);
        when(element.isVisible()).thenReturn(true);
        when(element.boundingBox()).thenReturn(first, second, second);
        return element;
    }

    private BoundingBox box(double width) {
        BoundingBox box = new BoundingBox();
        box.x = 0D;
        box.y = 0D;
        box.width = width;
        box.height = 40D;
        return box;
    }
}
