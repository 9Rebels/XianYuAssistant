package com.feijimiao.xianyuassistant.service;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Service;

@Service
public class SliderPageInspector {
    public boolean isReadyPunishSliderDom(SliderDomSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return snapshot.hasButton() && (snapshot.hasTrack() || snapshot.hasText());
    }

    public boolean isVerificationSuccess(PageState state) {
        if (state == null || state.isFailureSignal()) {
            return false;
        }
        if (state.isVisibleSlider()) {
            return false;
        }
        return !looksLikeVerificationUrl(state.getUrl())
                && !looksLikeVerificationTitle(state.getTitle())
                && !looksLikeVerificationText(state.getBodyText());
    }

    public boolean looksLikeVerificationUrl(String url) {
        String normalized = normalize(url);
        return normalized.contains("punish")
                || normalized.contains("captcha")
                || normalized.contains("x5step=2")
                || normalized.contains("action=captcha")
                || normalized.contains("verify");
    }

    public boolean looksLikeVerificationTitle(String title) {
        String normalized = normalize(title);
        return normalized.contains("验证")
                || normalized.contains("captcha")
                || normalized.contains("拦截");
    }

    public boolean looksLikeVerificationText(String bodyText) {
        String normalized = normalize(bodyText);
        return normalized.contains("滑动验证")
                || normalized.contains("请完成验证")
                || normalized.contains("验证码拦截")
                || normalized.contains("点击框体重试");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    public record SliderDomSnapshot(boolean hasButton, boolean hasTrack, boolean hasText) {
    }

    @Value
    @Builder
    public static class PageState {
        String url;
        String title;
        String bodyText;
        boolean visibleSlider;
        boolean failureSignal;
    }
}
