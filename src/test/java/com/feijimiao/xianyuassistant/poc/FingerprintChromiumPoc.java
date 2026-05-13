package com.feijimiao.xianyuassistant.poc;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * fingerprint-chromium 接入 PoC。
 *
 * 目的：单跑一次闲鱼账密登录流程，观察 nocaptcha 是否仍出现
 * "验证失败，点击框体重试(error:xxxx)" 硬拒绝。本类不进入生产代码，也不依赖 Spring 容器，可独立运行。
 *
 * 使用方法（PowerShell / cmd 均可，路径替换为本机实际值）：
 * <pre>
 *   mvn -DskipTests test-compile
 *   set CLASSPATH=target/classes;target/test-classes;%USERPROFILE%/.m2/repository/com/microsoft/playwright/playwright/1.49.0/playwright-1.49.0.jar;...其他 Playwright 依赖
 *   java -DfingerprintChromiumPath="C:/Tools/fingerprint-chromium/chrome.exe" ^
 *        -Dxianyu.username=15xxxxxxxxx ^
 *        -Dxianyu.password=YourPassword ^
 *        -cp %CLASSPATH% com.feijimiao.xianyuassistant.poc.FingerprintChromiumPoc
 * </pre>
 *
 * 推荐通过 IDE（IDEA / VSCode）直接 Run main 方法，能自动解析 classpath，省去依赖手抄。
 *
 * 关键参数：
 * <ul>
 *   <li>fingerprintChromiumPath：fingerprint-chromium 的可执行路径（Windows 上为 chrome.exe）。
 *       未指定时回退到 Playwright 默认 Chromium，便于做 A/B 对比。</li>
 *   <li>xianyu.username / xianyu.password：测试账密。必填。</li>
 *   <li>poc.headless：true/false，默认 false 便于肉眼观察。</li>
 *   <li>poc.userDataDir：持久化目录，默认 browser_data/fingerprint_poc。
 *       多次运行会复用同一目录，可观察 Cookie/会话累积效果。</li>
 *   <li>poc.observeSeconds：登录后保留浏览器观察时长（秒），默认 60。</li>
 * </ul>
 *
 * 判定标准：
 * <ul>
 *   <li>页面出现 "验证失败"/"点击框体重试" 文案 → 硬拒绝（fingerprint-chromium 仍被识别）。</li>
 *   <li>URL 跳出 passport.goofish.com 且能看到 IM 页 → 验证通过。</li>
 *   <li>滑块出现但能正常拖动且无硬拒绝文案 → 指纹层面规避成功，剩余问题在行为/IP。</li>
 * </ul>
 */
public class FingerprintChromiumPoc {

    private static final String LOGIN_URL = "https://www.goofish.com/im";
    private static final String HOMEPAGE_URL = "https://www.goofish.com";

    private static final List<String> ACCOUNT_SELECTORS = List.of(
            "#fm-login-id", "input[name=\"fm-login-id\"]",
            "input[placeholder*=\"手机号\"]", "input[placeholder*=\"账号\"]"
    );
    private static final List<String> PASSWORD_SELECTORS = List.of(
            "#fm-login-password", "input[name=\"fm-login-password\"]",
            "input[type=\"password\"]"
    );
    private static final List<String> SUBMIT_SELECTORS = List.of(
            "button.password-login", ".fm-button.fm-submit.password-login",
            ".password-login", "button.fm-submit"
    );
    private static final List<String> TAB_SELECTORS = List.of(
            "a.password-login-tab-item", ".password-login-tab-item",
            "text=密码登录", "text=账号密码登录"
    );

    public static void main(String[] args) throws Exception {
        String username = required("xianyu.username");
        String password = required("xianyu.password");
        String chromePath = System.getProperty("fingerprintChromiumPath");
        boolean headless = Boolean.parseBoolean(System.getProperty("poc.headless", "false"));
        long observeSeconds = Long.parseLong(System.getProperty("poc.observeSeconds", "60"));
        Path userDataDir = Path.of(System.getProperty(
                "poc.userDataDir",
                Path.of(System.getProperty("user.dir"), "browser_data", "fingerprint_poc").toString()));
        Files.createDirectories(userDataDir);

        log("chromePath=" + (chromePath == null ? "<Playwright 默认 Chromium>" : chromePath));
        log("userDataDir=" + userDataDir);
        log("headless=" + headless + ", observeSeconds=" + observeSeconds);

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(headless)
                    .setIgnoreDefaultArgs(List.of("--enable-automation"))
                    .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-blink-features=AutomationControlled",
                            "--no-first-run",
                            "--disable-dev-shm-usage"
                    ))
                    .setViewportSize(1366, 768)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setIgnoreHTTPSErrors(true);

            if (chromePath != null && !chromePath.isBlank()) {
                Path p = Path.of(chromePath);
                if (!Files.isExecutable(p)) {
                    log("⚠️ fingerprintChromiumPath 不可执行: " + p + "，将回退到 Playwright 默认 Chromium");
                } else {
                    options.setExecutablePath(p);
                }
            }

            try (BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options)) {
                Page page = context.newPage();

                log("访问首页预热...");
                page.navigate(HOMEPAGE_URL,
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(15000));
                page.waitForTimeout(2000);

                log("进入登录页...");
                page.navigate(LOGIN_URL,
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30000));
                page.waitForTimeout(3000);

                Frame loginFrame = findLoginFrame(page);
                if (loginFrame == null) {
                    log("未找到登录 iframe，使用 main frame 继续");
                    loginFrame = page.mainFrame();
                }

                clickFirstInFrame(loginFrame, TAB_SELECTORS);
                page.waitForTimeout(1000);

                ElementHandle accountInput = firstVisible(loginFrame, ACCOUNT_SELECTORS);
                if (accountInput == null) {
                    log("❌ 未找到账号输入框，可能页面结构变化或登录 iframe 解析失败");
                    return;
                }
                accountInput.click();
                accountInput.fill(username);
                page.waitForTimeout(500);

                ElementHandle passwordInput = firstVisible(loginFrame, PASSWORD_SELECTORS);
                if (passwordInput == null) {
                    log("❌ 未找到密码输入框");
                    return;
                }
                passwordInput.click();
                passwordInput.fill(password);
                page.waitForTimeout(700);

                tryCheckAgreement(loginFrame);

                log("点击登录按钮，开始观察 nocaptcha 表现...");
                boolean clicked = clickFirstInFrame(loginFrame, SUBMIT_SELECTORS);
                log("登录按钮点击结果: " + clicked);
                if (!clicked) {
                    passwordInput.press("Enter");
                    log("回退到 Enter 键提交");
                }

                pollResult(page, observeSeconds);

                log("浏览器保留 30s 供肉眼观察，期间可手动拖动滑块/扫码。");
                page.waitForTimeout(30_000);
            }
        }
    }

    private static void pollResult(Page page, long observeSeconds) {
        BrowserContext context = page.context();
        long deadline = System.currentTimeMillis() + observeSeconds * 1000L;
        String lastUrl = "";
        boolean hardBlockSeen = false;
        boolean sliderSeen = false;
        boolean loggedIn = false;
        long lastSnapshot = 0L;
        while (System.currentTimeMillis() < deadline) {
            String url = page.url();
            if (!url.equals(lastUrl)) {
                log("URL=" + url);
                lastUrl = url;
            }
            if (!sliderSeen && hasAnyVisible(page, List.of("#nc_1_n1z", ".nc-container", ".btn_slide"))) {
                sliderSeen = true;
                log("→ 检测到滑块 DOM，nocaptcha 进入挑战阶段");
            }
            if (!hardBlockSeen && hasAnyTextVisible(page,
                    List.of("验证失败，点击框体重试", "验证失败", "点击框体重试", "框体错误"))) {
                hardBlockSeen = true;
                log("❌ 出现硬拒绝文案，fingerprint-chromium 仍被识别（或行为/IP 触发风控）");
            }
            // 真正的登录判定：cookies 里要有 unb（用户标识）
            if (!loggedIn) {
                try {
                    boolean hasUnb = context.cookies(List.of(
                            "https://www.goofish.com", "https://h5api.m.goofish.com",
                            "https://www.taobao.com", "https://login.taobao.com")).stream()
                            .anyMatch(c -> "unb".equals(c.name) && c.value != null && !c.value.isBlank());
                    if (hasUnb) {
                        loggedIn = true;
                        log("✅ Cookie 中已出现 unb，登录态有效，URL=" + url);
                    }
                } catch (Exception ignored) {
                }
            }
            // 每 10 秒强制输出一次状态快照，便于观察"什么都没发生"的情况
            long now = System.currentTimeMillis();
            if (now - lastSnapshot >= 10_000) {
                lastSnapshot = now;
                snapshot(page, context, sliderSeen, hardBlockSeen, loggedIn);
            }
            page.waitForTimeout(2000);
        }

        log("观察结束。结论：sliderSeen=" + sliderSeen
                + ", hardBlockSeen=" + hardBlockSeen
                + ", loggedIn=" + loggedIn);
        snapshot(page, context, sliderSeen, hardBlockSeen, loggedIn);
    }

    private static void snapshot(Page page, BrowserContext context,
                                 boolean sliderSeen, boolean hardBlockSeen, boolean loggedIn) {
        try {
            int frames = page.frames().size();
            int cookieCount = context.cookies().size();
            // 抓登录错误提示，闲鱼用 .error-tip / .next-form-item-msg 等
            String errorText = "";
            for (String sel : List.of(
                    "div.error-tip", "div.error-msg", ".password-error", ".error-info",
                    ".sufei-dialog-content", "[class*='error']", "text=账号或密码")) {
                try {
                    var loc = page.locator(sel);
                    if (loc.count() > 0) {
                        String text = loc.first().textContent();
                        if (text != null && !text.isBlank()) {
                            errorText = sel + " => " + text.trim();
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            log("[snapshot] frames=" + frames + " cookieCount=" + cookieCount
                    + " sliderSeen=" + sliderSeen + " hardBlockSeen=" + hardBlockSeen
                    + " loggedIn=" + loggedIn
                    + (errorText.isBlank() ? "" : " errorText=[" + errorText + "]"));
        } catch (Exception e) {
            log("[snapshot] failed: " + e.getMessage());
        }
    }

    private static void tryCheckAgreement(Frame frame) {
        try {
            ElementHandle checkbox = frame.querySelector("#fm-agreement-checkbox, input[type=\"checkbox\"]");
            if (checkbox != null && checkbox.isVisible() && !checkbox.isChecked()) {
                checkbox.click();
            }
        } catch (Exception ignored) {
        }
    }

    private static Frame findLoginFrame(Page page) {
        for (Frame f : page.frames()) {
            if (f == page.mainFrame()) {
                continue;
            }
            for (String selector : ACCOUNT_SELECTORS) {
                try {
                    ElementHandle el = f.querySelector(selector);
                    if (el != null && el.isVisible()) {
                        return f;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        for (String selector : ACCOUNT_SELECTORS) {
            try {
                ElementHandle el = page.querySelector(selector);
                if (el != null && el.isVisible()) {
                    return page.mainFrame();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean clickFirstInFrame(Frame frame, List<String> selectors) {
        for (String s : selectors) {
            try {
                ElementHandle el = frame.querySelector(s);
                if (el != null && el.isVisible()) {
                    el.click();
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static ElementHandle firstVisible(Frame frame, List<String> selectors) {
        for (String s : selectors) {
            try {
                ElementHandle el = frame.querySelector(s);
                if (el != null && el.isVisible()) {
                    return el;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static boolean hasAnyVisible(Page page, List<String> selectors) {
        for (String s : selectors) {
            try {
                if (page.locator(s).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean hasAnyTextVisible(Page page, List<String> texts) {
        for (String t : texts) {
            try {
                if (page.locator("text=" + t).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必需系统属性: -D" + key + "=...");
        }
        return value;
    }

    private static void log(String message) {
        System.out.println("[fp-poc " + java.time.LocalTime.now() + "] " + message);
    }
}
