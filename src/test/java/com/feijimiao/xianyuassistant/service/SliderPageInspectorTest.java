package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderPageInspectorTest {

    @Test
    void readyPunishDomRequiresButtonAndTrackOrText() {
        SliderPageInspector inspector = new SliderPageInspector();
        SliderPageInspector.SliderDomSnapshot readyWithTrack =
                new SliderPageInspector.SliderDomSnapshot(true, true, false);
        SliderPageInspector.SliderDomSnapshot readyWithText =
                new SliderPageInspector.SliderDomSnapshot(true, false, true);

        assertTrue(inspector.isReadyPunishSliderDom(readyWithTrack));
        assertTrue(inspector.isReadyPunishSliderDom(readyWithText));
    }

    @Test
    void sliderMissingAloneIsNotEnoughForSuccessOnPunishUrl() {
        SliderPageInspector inspector = new SliderPageInspector();
        SliderPageInspector.PageState state = SliderPageInspector.PageState.builder()
                .url("https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha")
                .title("验证码拦截")
                .bodyText("")
                .visibleSlider(false)
                .failureSignal(false)
                .build();

        assertFalse(inspector.isVerificationSuccess(state));
    }

    @Test
    void successRequiresLeavingVerificationRouteWithoutFailureSignal() {
        SliderPageInspector inspector = new SliderPageInspector();
        SliderPageInspector.PageState state = SliderPageInspector.PageState.builder()
                .url("https://www.goofish.com/im")
                .title("闲鱼")
                .bodyText("")
                .visibleSlider(false)
                .failureSignal(false)
                .build();

        assertTrue(inspector.isVerificationSuccess(state));
    }
}
