package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.config.SliderProperties;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SliderAutoVerifyServiceTest {

    @Test
    void recalibrateStartPointUsesHoveredBoundingBoxCenter() {
        SliderAutoVerifyService service = new SliderAutoVerifyService();
        ElementHandle button = mock(ElementHandle.class);

        BoundingBox hoveredBox = new BoundingBox();
        hoveredBox.x = 150D;
        hoveredBox.y = 60D;
        hoveredBox.width = 40D;
        hoveredBox.height = 20D;

        when(button.boundingBox()).thenReturn(hoveredBox);

        SliderAutoVerifyService.PointerOrigin origin = ReflectionTestUtils.invokeMethod(
                service, "recalibrateStartPoint", 100D, 50D, button);

        assertEquals(170D, origin.x(), 0.001D);
        assertEquals(70D, origin.y(), 0.001D);
    }

    @Test
    void recalibrateStartPointFallsBackWhenBoundingBoxMissing() {
        SliderAutoVerifyService service = new SliderAutoVerifyService();
        ElementHandle button = mock(ElementHandle.class);
        when(button.boundingBox()).thenReturn(null);

        SliderAutoVerifyService.PointerOrigin origin = ReflectionTestUtils.invokeMethod(
                service, "recalibrateStartPoint", 100D, 50D, button);

        assertEquals(100D, origin.x(), 0.001D);
        assertEquals(50D, origin.y(), 0.001D);
    }

    @Test
    void hesitationOnlyTriggersInMiddleProgressWindow() {
        SliderAutoVerifyService service = new SliderAutoVerifyService();

        Boolean beforeWindow = ReflectionTestUtils.invokeMethod(service, "isHesitationWindow", 0, 10);
        Boolean earlyMiddle = ReflectionTestUtils.invokeMethod(service, "isHesitationWindow", 2, 10);
        Boolean lateMiddle = ReflectionTestUtils.invokeMethod(service, "isHesitationWindow", 8, 10);
        Boolean afterWindow = ReflectionTestUtils.invokeMethod(service, "isHesitationWindow", 9, 10);

        assertFalse(beforeWindow);
        assertTrue(earlyMiddle);
        assertTrue(lateMiddle);
        assertFalse(afterWindow);
    }

    @Test
    void moveTrajectoryRepositionsToHoveredCenterBeforeDrag() {
        SliderAutoVerifyService service = new SliderAutoVerifyService();
        Page page = mock(Page.class);
        Mouse mouse = mock(Mouse.class);
        ElementHandle button = mock(ElementHandle.class);

        BoundingBox initialBox = new BoundingBox();
        initialBox.x = 100D;
        initialBox.y = 50D;
        initialBox.width = 40D;
        initialBox.height = 20D;

        BoundingBox hoveredBox = new BoundingBox();
        hoveredBox.x = 130D;
        hoveredBox.y = 65D;
        hoveredBox.width = 40D;
        hoveredBox.height = 20D;

        when(page.mouse()).thenReturn(mouse);
        when(button.boundingBox()).thenReturn(initialBox, hoveredBox);

        List<SliderTrajectoryPlanner.TrajectoryPoint> points = List.of(
                new SliderTrajectoryPlanner.TrajectoryPoint(10D, 0D, 1D),
                new SliderTrajectoryPlanner.TrajectoryPoint(20D, 0D, 1D)
        );
        SliderTrajectoryPlanner.TrajectoryPlan plan =
                new SliderTrajectoryPlanner.TrajectoryPlan("test", points, 1.05D, 1D, 1.8D);

        ReflectionTestUtils.invokeMethod(service, "simulateSlide", page, button, plan, 1);

        verify(button, atLeastOnce()).hover(any(ElementHandle.HoverOptions.class));
        verify(mouse, atLeastOnce()).move(150D, 75D);
    }

    @Test
    void simulateSlideClicksNearFinalPointAfterMouseUp() {
        SliderAutoVerifyService service = new SliderAutoVerifyService();
        Page page = mock(Page.class);
        Mouse mouse = mock(Mouse.class);
        ElementHandle button = mock(ElementHandle.class);

        BoundingBox box = new BoundingBox();
        box.x = 100D;
        box.y = 50D;
        box.width = 40D;
        box.height = 20D;

        when(page.mouse()).thenReturn(mouse);
        when(button.boundingBox()).thenReturn(box, box);

        List<SliderTrajectoryPlanner.TrajectoryPoint> points = List.of(
                new SliderTrajectoryPlanner.TrajectoryPoint(10D, 1D, 1D),
                new SliderTrajectoryPlanner.TrajectoryPoint(21D, 2D, 1D)
        );
        SliderTrajectoryPlanner.TrajectoryPlan plan =
                new SliderTrajectoryPlanner.TrajectoryPlan("test", points, 1.05D, 1D, 1.8D);

        ReflectionTestUtils.invokeMethod(service, "simulateSlide", page, button, plan, 1);

        verify(mouse, atLeastOnce()).click(eq(141D), eq(62D));
    }

    @Test
    void failureScreenshotDefaultsToDbdataCaptchaDebugAccountFolder() {
        SliderAutoVerifyService service = new SliderAutoVerifyService();
        SliderProperties.FailureCapture config = new SliderProperties().getFailureCapture();

        Path target = ReflectionTestUtils.invokeMethod(service, "resolveScreenshotPath",
                config, 3L, 2, "verify_failed");

        String normalized = target.toString().replace('\\', '/');
        assertTrue(normalized.endsWith("/dbdata/captcha-debug/3/"
                + target.getFileName().toString()));
        assertTrue(target.getFileName().toString().contains("_attempt2_verify_failed.png"));
    }
}
