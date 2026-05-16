package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
class PasswordLoginPageHelper {
    private static final List<String> ACCOUNT_SELECTORS = List.of(
            "#fm-login-id", "input[name=\"fm-login-id\"]",
            "input[placeholder*=\"手机号\"]", "input[placeholder*=\"手机\"]",
            "input[placeholder*=\"邮箱\"]", "input[placeholder*=\"账号\"]",
            ".fm-login-id", "#J_LoginForm input[type=\"text\"]", "#TPL_username_1"
    );
    private static final List<String> PASSWORD_SELECTORS = List.of(
            "#fm-login-password", "input[name=\"fm-login-password\"]",
            "input[type=\"password\"]", "input[placeholder*=\"密码\"]", "#TPL_password_1"
    );
    private static final List<String> SUBMIT_SELECTORS = List.of(
            "button.password-login", ".fm-button.fm-submit.password-login",
            ".password-login", "button.fm-submit", "text=登录"
    );
    private static final List<String> TAB_SELECTORS = List.of(
            "a.password-login-tab-item", ".password-login-tab-item",
            "text=密码登录", "text=账号密码登录"
    );
    private static final List<String> SLIDER_SELECTORS = List.of(
            "#nc_1_n1z", ".nc-container", ".nc_scale", ".nc-wrapper", ".btn_slide"
    );

    Frame findLoginFrame(Page page) {
        return findLoginFrameWithRetry(page, 10_000, 800);
    }

    /**
     * 轮询查找登录frame。goofish.com/im 的登录弹窗 iframe 由 SPA 动态创建，
     * DOMContentLoaded 后仍需等待 JS 渲染，必须轮询而非单次检查。
     */
    private Frame findLoginFrameWithRetry(Page page, long timeoutMs, long pollIntervalMs) {
        page.waitForTimeout(1000);
        long deadline = System.currentTimeMillis() + timeoutMs;
        int pollCount = 0;
        while (System.currentTimeMillis() < deadline) {
            pollCount++;
            List<Frame> frames = page.frames();
            if (pollCount == 1 || pollCount % 3 == 0) {
                log.info("[PasswordLogin] 第{}次轮询查找登录frame, 共{}个frame", pollCount, frames.size());
            }
            for (Frame frame : frames) {
                if (frame == page.mainFrame()) {
                    continue;
                }
                try {
                    if (findAccountInput(frame) != null) {
                        log.info("[PasswordLogin] 在iframe中找到登录表单: {}", frame.url());
                        return frame;
                    }
                } catch (Exception ignored) {}
            }
            if (findFirst(page, ACCOUNT_SELECTORS) != null) {
                log.info("[PasswordLogin] 在主页面找到登录表单");
                return page.mainFrame();
            }
            // 也检查是否有滑块（有登录态时直接弹滑块，没有登录表单）
            if (hasSliderInAnyFrame(page)) {
                log.info("[PasswordLogin] 轮询中检测到滑块（可能有登录态），返回主frame");
                return page.mainFrame();
            }
            page.waitForTimeout(pollIntervalMs);
        }
        log.warn("[PasswordLogin] 轮询{}次后仍未找到登录frame", pollCount);
        return null;
    }

    ElementHandle findAccountInput(Frame frame) {
        return findFirstInFrame(frame, ACCOUNT_SELECTORS);
    }

    ElementHandle findPasswordInput(Frame frame) {
        return findFirstInFrame(frame, PASSWORD_SELECTORS);
    }

    boolean clickPasswordLoginTab(Frame frame) {
        return clickFirstInFrame(frame, TAB_SELECTORS);
    }

    boolean clickSubmit(Frame frame) {
        return clickFirstInFrame(frame, SUBMIT_SELECTORS);
    }

    boolean hasSliderInAnyFrame(Page page) {
        for (Frame frame : page.frames()) {
            for (String selector : SLIDER_SELECTORS) {
                try {
                    ElementHandle el = frame.querySelector(selector);
                    if (el != null && el.isVisible()) {
                        log.info("[PasswordLogin] 在frame中检测到滑块元素: {}", selector);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    String snapshotText(Page page) {
        if (page == null || page.isClosed()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Frame frame : page.frames()) {
            try {
                String text = frame.textContent("body");
                if (text != null && !text.isBlank()) {
                    builder.append(text).append('\n');
                }
            } catch (Exception ignored) {}
        }
        if (builder.isEmpty()) {
            try {
                builder.append(page.content());
            } catch (Exception ignored) {}
        }
        return builder.toString();
    }

    private ElementHandle findFirstInFrame(Frame frame, List<String> selectors) {
        for (String selector : selectors) {
            try {
                ElementHandle el = frame.querySelector(selector);
                if (el != null && el.isVisible()) {
                    return el;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean clickFirstInFrame(Frame frame, List<String> selectors) {
        ElementHandle el = findFirstInFrame(frame, selectors);
        if (el == null) {
            return false;
        }
        el.click();
        return true;
    }

    private ElementHandle findFirst(Page page, List<String> selectors) {
        for (String selector : selectors) {
            try {
                ElementHandle el = page.querySelector(selector);
                if (el != null && el.isVisible()) {
                    return el;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
