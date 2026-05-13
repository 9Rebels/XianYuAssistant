package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class SliderCaptchaBlockDetector {
    static final String[] BUTTON_SELECTORS = {
            "#nc_1_n1z",
            ".btn_slide",
            ".sm-btn"
    };
    static final String[] TRACK_SELECTORS = {
            "#nc_1_n1t",
            ".nc_scale"
    };
    private static final String[] TEXT_SELECTORS = {
            "#nc_1__scale_text",
            ".captcha-tips"
    };
    private static final String[] SHELL_SELECTORS = {
            ".errloading",
            "[data-nc-status='error']",
            "#nocaptcha",
            ".nc-container",
            ".nc_wrapper",
            ".nc_scale",
            ".sm-btn-wrapper",
            "#baxia-dialog-content"
    };
    private static final String[] ACTIVATION_SELECTORS = {
            ".errloading",
            ".nc-lang-cnt .errloading",
            "[data-nc-status='error']",
            ".nc-container",
            "#nocaptcha",
            ".nc_wrapper",
            ".nc_scale",
            ".sm-btn-wrapper",
            "#baxia-dialog-content",
            "#nc_1__bg"
    };
    private static final String[] FAILURE_KEYWORDS = {
            "框体错误",
            "验证失败，点击框体重试",
            "点击框体重试",
            "请重试",
            "验证码错误",
            "滑动验证失败",
            "try again",
            "failed"
    };
    private static final String[] FAILURE_SELECTORS = {
            "text=验证失败，点击框体重试",
            "text=框体错误",
            "text=点击框体重试",
            ".errloading",
            ".sm-btn-fail",
            ".wrong-cross",
            "[class*='retry']",
            "[class*='fail']",
            "[class*='error']",
            ".captcha-tips"
    };

    SliderCaptchaBlock detect(SliderSearchTarget target) {
        if (target == null || target.isDetached()) {
            return null;
        }
        String currentUrl = safeUrl(target);
        String currentTitle = safeTitle(target);
        String pageText = safeContent(target);
        boolean hasOperableSlider = hasOperableSlider(target);
        if (isPunishCaptcha(currentUrl, currentTitle, pageText) && !hasOperableSlider) {
            return new SliderCaptchaBlock(
                    "punish_captcha",
                    currentUrl,
                    currentTitle,
                    "当前命中阿里验证码拦截处罚页（pureCaptcha），且页面不存在可操作滑块"
            );
        }
        if (isFeedbackBlock(target, pageText) && !hasOperableSlider) {
            return new SliderCaptchaBlock(
                    "feedback_block",
                    currentUrl,
                    currentTitle,
                    "当前命中反馈二维码/处罚页，不存在可操作滑块"
            );
        }
        if (hasRestrictedAccountText(pageText)) {
            return new SliderCaptchaBlock(
                    "account_restricted",
                    currentUrl,
                    currentTitle,
                    "当前页面命中账号受限/安全验证未通过提示"
            );
        }
        return null;
    }

    SliderCaptchaBlock waitForPunishSliderReadyIfNeeded(Page page,
                                                        SliderSearchTarget target,
                                                        SliderCaptchaBlock block,
                                                        String contextLabel) {
        if (block == null || !"punish_captcha".equals(block.getKind())) {
            return block;
        }
        if (hasReadyPunishSliderDom(target)) {
            log.info("{} 检测到处罚页真实滑块DOM已就绪，继续按正常滑块处理", contextLabel);
            return null;
        }
        long deadline = System.currentTimeMillis() + 1200L;
        SliderCaptchaBlock refreshed = block;
        while (System.currentTimeMillis() < deadline) {
            safeWait(page, 250D);
            if (hasReadyPunishSliderDom(target)) {
                log.info("{} 处罚页真实滑块DOM已延迟出现，继续按正常滑块处理", contextLabel);
                return null;
            }
            refreshed = detect(target);
            if (refreshed == null) {
                log.info("{} 处罚页状态已恢复，继续按正常滑块处理", contextLabel);
                return null;
            }
        }
        return refreshed;
    }

    SliderCaptchaBlock recoverPunishShellIfPossible(Page page,
                                                    SliderSearchTarget target,
                                                    SliderCaptchaBlock block,
                                                    String contextLabel) {
        if (block == null || !"punish_captcha".equals(block.getKind())) {
            return block;
        }
        if (!hasRecoverablePunishSliderShell(target)) {
            return block;
        }
        if (!clickFirstActivationTarget(page, target, contextLabel)) {
            return block;
        }
        safeWait(page, 800D);
        SliderCaptchaBlock refreshed = detect(target);
        if (refreshed == null) {
            log.info("{} pureCaptcha 壳页已点活，继续按正常滑块处理", contextLabel);
        } else {
            log.info("{} pureCaptcha 壳页点活后仍是硬拦截[{}]", contextLabel, refreshed.getKind());
        }
        return refreshed;
    }

    boolean hasReadyPunishSliderDom(SliderSearchTarget target) {
        if (target == null || target.isDetached()) {
            return false;
        }
        boolean hasButton = hasAnyElement(target, BUTTON_SELECTORS);
        boolean hasTrack = hasAnyElement(target, TRACK_SELECTORS);
        boolean hasText = hasNonBlankText(target, TEXT_SELECTORS);
        return hasButton && (hasTrack || hasText);
    }

    boolean hasRecoverablePunishSliderShell(SliderSearchTarget target) {
        return hasAnyVisibleElement(target, SHELL_SELECTORS);
    }

    boolean hasOperableSlider(SliderSearchTarget target) {
        return hasAnyVisibleElement(target, BUTTON_SELECTORS)
                || hasAnyVisibleElement(target, TRACK_SELECTORS);
    }

    boolean hasFailureSignal(SliderSearchTarget target) {
        String content = safeContent(target);
        for (String keyword : FAILURE_KEYWORDS) {
            if (containsIgnoreCase(content, keyword)) {
                log.info("{} 内容包含验证失败关键词: {}", target.label(), keyword);
                return true;
            }
        }
        for (String selector : FAILURE_SELECTORS) {
            ElementHandle element = safeQuery(target, selector);
            if (element != null && isVisible(element)) {
                log.info("{} 找到验证失败提示元素: selector={}", target.label(), selector);
                return true;
            }
        }
        return false;
    }

    boolean clickFailureRetryIfPresent(Page page, SliderSearchTarget target, String contextLabel) {
        if (target == null || target.isDetached() || !hasFailureSignal(target)) {
            return false;
        }
        for (String selector : FAILURE_SELECTORS) {
            ElementHandle element = safeQuery(target, selector);
            if (element == null || !isVisible(element)) {
                continue;
            }
            try {
                BoundingBox box = element.boundingBox();
                if (box != null && page != null) {
                    page.mouse().click(box.x + box.width / 2, box.y + box.height / 2);
                } else {
                    element.click(new ElementHandle.ClickOptions().setTimeout(1000));
                }
                log.info("已点击{}普通失败重试区域: target={}, selector={}",
                        contextLabel, target.label(), selector);
                return true;
            } catch (Exception e) {
                log.debug("点击{}普通失败重试区域失败: selector={}, error={}",
                        contextLabel, selector, e.getMessage());
            }
        }
        return false;
    }

    private boolean clickFirstActivationTarget(Page page, SliderSearchTarget target, String contextLabel) {
        for (String selector : ACTIVATION_SELECTORS) {
            ElementHandle element = safeQuery(target, selector);
            if (element == null || !isVisible(element)) {
                continue;
            }
            try {
                BoundingBox box = element.boundingBox();
                if (box != null && page != null) {
                    page.mouse().click(box.x + box.width / 2, box.y + box.height / 2);
                } else {
                    element.click(new ElementHandle.ClickOptions().setTimeout(1000));
                }
                log.info("已点击{}激活区域: target={}, selector={}",
                        contextLabel, target.label(), selector);
                return true;
            } catch (Exception e) {
                log.debug("点击{}激活区域失败: selector={}, error={}",
                        contextLabel, selector, e.getMessage());
            }
        }
        return false;
    }

    private boolean isPunishCaptcha(String url, String title, String bodyText) {
        String normalizedUrl = normalize(url);
        int hitCount = 0;
        for (String token : new String[]{"punish?x5secdata", "action=captcha", "purecaptcha=true", "x5step=2"}) {
            if (normalizedUrl.contains(token)) {
                hitCount++;
            }
        }
        return hitCount >= 2
                || containsIgnoreCase(title, "验证码拦截")
                || containsIgnoreCase(title, "captcha intercept")
                || containsIgnoreCase(bodyText, "验证码拦截")
                || containsIgnoreCase(bodyText, "验证失败，点击框体重试");
    }

    private boolean isFeedbackBlock(SliderSearchTarget target, String bodyText) {
        boolean keywordHit = containsIgnoreCase(bodyText, "抱歉，页面访问出现了问题")
                || containsIgnoreCase(bodyText, "页面访问出现了问题")
                || containsIgnoreCase(bodyText, "点我反馈");
        boolean markerHit = hasAnyElement(target, new String[]{
                ".bx-pu-qrcode-wrap",
                ".captcha-qrcode",
                "#bx-feedback-btn",
                "a[href*='page/feedback']"
        });
        return keywordHit && markerHit;
    }

    private boolean hasRestrictedAccountText(String bodyText) {
        for (String keyword : new String[]{
                "账号已被限制",
                "限制访问",
                "账号异常",
                "账号被冻结",
                "暂时无法使用",
                "安全验证未通过",
                "账户被限制"
        }) {
            if (containsIgnoreCase(bodyText, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyElement(SliderSearchTarget target, String[] selectors) {
        for (String selector : selectors) {
            if (safeQuery(target, selector) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyVisibleElement(SliderSearchTarget target, String[] selectors) {
        for (String selector : selectors) {
            ElementHandle element = safeQuery(target, selector);
            if (element != null && isVisible(element)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonBlankText(SliderSearchTarget target, String[] selectors) {
        for (String selector : selectors) {
            ElementHandle element = safeQuery(target, selector);
            if (element != null && !safeText(element).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private ElementHandle safeQuery(SliderSearchTarget target, String selector) {
        try {
            return target.querySelector(selector);
        } catch (Exception e) {
            log.debug("查找滑块验证元素失败: target={}, selector={}, error={}",
                    target.label(), selector, e.getMessage());
            return null;
        }
    }

    private boolean isVisible(ElementHandle element) {
        try {
            return element.isVisible();
        } catch (Exception e) {
            return true;
        }
    }

    private String safeText(ElementHandle element) {
        try {
            String text = element.textContent();
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeContent(SliderSearchTarget target) {
        try {
            String content = target.content();
            return content == null ? "" : content;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeTitle(SliderSearchTarget target) {
        try {
            String title = target.title();
            return title == null ? "" : title;
        } catch (Exception e) {
            return "";
        }
    }

    private String safeUrl(SliderSearchTarget target) {
        try {
            String url = target.url();
            return url == null ? "" : url;
        } catch (Exception e) {
            return "";
        }
    }

    private void safeWait(Page page, double timeoutMs) {
        try {
            if (page != null && !page.isClosed()) {
                page.waitForTimeout(timeoutMs);
            }
        } catch (Exception ignored) {
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private boolean containsIgnoreCase(String text, String token) {
        return normalize(text).contains(normalize(token));
    }
}
