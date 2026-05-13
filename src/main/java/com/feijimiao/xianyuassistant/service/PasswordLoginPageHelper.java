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
        page.waitForTimeout(1000);
        List<Frame> frames = page.frames();
        log.info("[PasswordLogin] 页面共有 {} 个frame", frames.size());

        for (Frame frame : frames) {
            if (frame == page.mainFrame()) {
                continue;
            }
            try {
                String frameUrl = frame.url();
                log.debug("[PasswordLogin] 检查frame: {}", frameUrl);
                if (findAccountInput(frame) != null) {
                    log.info("[PasswordLogin] 在iframe中找到登录表单: {}", frameUrl);
                    return frame;
                }
            } catch (Exception ignored) {}
        }

        if (findFirst(page, ACCOUNT_SELECTORS) != null) {
            log.info("[PasswordLogin] 在主页面找到登录表单");
            return page.mainFrame();
        }
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
