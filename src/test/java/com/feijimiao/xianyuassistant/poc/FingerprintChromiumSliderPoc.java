package com.feijimiao.xianyuassistant.poc;

import com.feijimiao.xianyuassistant.service.SliderAutoVerifyService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * fingerprint-chromium 滑块验证 PoC。
 *
 * 跳过账密登录，直接从数据库（dbdata/xianyu_assistant.db）读取已登录账号的 Cookie，
 * 注入到 fingerprint-chromium 启动的浏览器上下文，导航到 IM 页（可能触发风控滑块），
 * 然后调用主代码里同款的 SliderAutoVerifyService 处理。
 *
 * 与主链路（XianyuSliderStealthService）保持一致的关键点：
 *   - userDataDir 默认复用 browser_data/user_{accountId}，沿用账号本地 IndexedDB / localStorage
 *   - 启动后注入 stealth init script，抹掉 navigator.webdriver 等自动化痕迹
 *   - 探测 fingerprint-chromium 真实版本，UA 与 sec-ch-ua / userAgentData 保持一致
 *   - warmup 先访问首页再进 IM，避免被前端判定"无会话直入"
 *   - 注入 cookie 后追加 document.cookie + mtop login.token 的诊断输出
 *
 * 注意：browser_data/user_{accountId} 与主应用的 XianyuSliderStealthService 共享 profile，
 *      请在主应用未在跑滑块流程时运行 PoC，否则会出现 SingletonLock 冲突。
 *
 * 用法（IDEA 中跑 main 即可）：
 *   -DfingerprintChromiumPath=C:/Users/Ran/AppData/Local/Chromium/Application/chrome.exe
 *   -Dpoc.accountId=1
 *   -Dpoc.dbPath=dbdata/xianyu_assistant.db
 *   -Dpoc.targetUrl=https://www.goofish.com/im
 *   -Dpoc.headless=false
 *   -Dpoc.observeSeconds=120
 *   -Dpoc.userDataDir=<可选，默认 browser_data/user_{accountId}>
 */
public class FingerprintChromiumSliderPoc {

    private static final String HOMEPAGE_URL = "https://www.goofish.com/";
    private static final String LOGIN_TOKEN_URL =
            "https://h5api.m.goofish.com/h5/mtop.taobao.idlemessage.pc.login.token/1.0/"
                    + "?jsv=2.7.2&appKey=34839810&type=originaljson&dataType=json&v=1.0"
                    + "&api=mtop.taobao.idlemessage.pc.login.token&sessionOption=AutoLoginOnly";
    private static final String FALLBACK_VERSION = "120.0.0.0";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+){0,3})");

    public static void main(String[] args) throws Exception {
        long accountId = Long.parseLong(System.getProperty("poc.accountId", "1"));
        String dbPath = System.getProperty("poc.dbPath", "dbdata/xianyu_assistant.db");
        String targetUrl = System.getProperty("poc.targetUrl", "https://www.goofish.com/im");
        String chromePath = System.getProperty("fingerprintChromiumPath");
        boolean headless = Boolean.parseBoolean(System.getProperty("poc.headless", "false"));
        long observeSeconds = Long.parseLong(System.getProperty("poc.observeSeconds", "120"));
        Path defaultProfile = Path.of(System.getProperty("user.dir"), "browser_data", "user_" + accountId);
        Path userDataDir = Path.of(System.getProperty("poc.userDataDir", defaultProfile.toString()));
        Files.createDirectories(userDataDir);

        String cookieText = readCookieFromDb(dbPath, accountId);
        if (cookieText == null || cookieText.isBlank()) {
            log("❌ 数据库未找到账号 " + accountId + " 的 cookie，accountId 是否正确？");
            return;
        }
        String chromiumVersion = detectChromiumVersion(chromePath);
        String userAgent = buildUserAgent(chromiumVersion);
        log("从 DB 读到 cookie，accountId=" + accountId + ", length=" + cookieText.length());
        log("chromePath=" + (chromePath == null ? "<Playwright 默认 Chromium>" : chromePath));
        log("chromiumVersion=" + chromiumVersion);
        log("userAgent=" + userAgent);
        log("userDataDir=" + userDataDir);
        log("targetUrl=" + targetUrl + ", headless=" + headless + ", observeSeconds=" + observeSeconds);

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(headless)
                    .setIgnoreDefaultArgs(List.of("--enable-automation"))
                    .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-blink-features=AutomationControlled",
                            "--no-first-run",
                            "--disable-dev-shm-usage",
                            "--no-default-browser-check",
                            "--force-color-profile=srgb",
                            "--password-store=basic",
                            "--use-mock-keychain",
                            "--mute-audio",
                            "--lang=zh-CN",
                            "--accept-lang=zh-CN,zh;q=0.9,en;q=0.8"
                    ))
                    .setViewportSize(1600, 900)
                    .setScreenSize(1600, 900)
                    .setUserAgent(userAgent)
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setIgnoreHTTPSErrors(true)
                    .setExtraHTTPHeaders(Map.of(
                            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8",
                            "sec-ch-ua", secChUa(chromiumVersion),
                            "sec-ch-ua-mobile", "?0",
                            "sec-ch-ua-platform", "\"Windows\""
                    ));
            if (chromePath != null && !chromePath.isBlank()) {
                Path p = Path.of(chromePath);
                if (Files.isExecutable(p)) {
                    options.setExecutablePath(p);
                }
            }

            try (BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir, options)) {
                // 反检测：抹掉 navigator.webdriver 等自动化痕迹，保持 sec-ch-ua/userAgentData 一致
                context.addInitScript(buildStealthScript(userAgent, chromiumVersion));

                // 注入 cookie 到 .goofish.com 和 .taobao.com 域
                List<Cookie> cookies = buildPlaywrightCookies(cookieText);
                context.addCookies(cookies);
                log("已注入 cookies: " + cookies.size() + " 条");

                // persistent context 通常已带一个 about:blank 页面，复用以避免出现多 tab
                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

                // warmup：先访问首页建立会话，再跳目标页
                warmupAndNavigate(page, targetUrl);

                // 诊断：实际可见的 document.cookie + mtop login.token 是否被服务端接受
                diagnoseLoginState(page);

                // 检测页面上是否有滑块
                if (hasSlider(page)) {
                    log("→ 页面出现滑块，调用 SliderAutoVerifyService 自动处理...");
                    SliderAutoVerifyService slider = new SliderAutoVerifyService();
                    boolean ok = slider.solve(page, accountId);
                    log("滑块结果: success=" + ok + ", 当前 hardBlock="
                            + hasHardBlockText(page));
                } else {
                    log("当前页面无滑块 DOM，开始观察是否会延迟出现 nocaptcha...");
                }

                // 观察阶段
                long deadline = System.currentTimeMillis() + observeSeconds * 1000L;
                String lastUrl = page.url();
                boolean sliderHandled = false;
                while (System.currentTimeMillis() < deadline) {
                    String url = page.url();
                    if (!url.equals(lastUrl)) {
                        log("URL 变化: " + url);
                        lastUrl = url;
                    }
                    if (!sliderHandled && hasSlider(page)) {
                        log("→ 延迟出现滑块，调用 SliderAutoVerifyService...");
                        SliderAutoVerifyService slider = new SliderAutoVerifyService();
                        boolean ok = slider.solve(page, accountId);
                        log("滑块结果: success=" + ok + ", 当前 hardBlock="
                                + hasHardBlockText(page));
                        sliderHandled = true;
                    }
                    if (hasHardBlockText(page)) {
                        log("❌ 出现硬拒绝文案（验证失败/点击框体重试）");
                        break;
                    }
                    page.waitForTimeout(3000);
                }
                log("观察结束。浏览器保留 30s 供肉眼检查...");
                page.waitForTimeout(30_000);
            }
        }
    }

    private static String readCookieFromDb(String dbPath, long accountId) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Throwable ignored) {
        }
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT cookie_text FROM xianyu_cookie WHERE xianyu_account_id=? "
                             + "ORDER BY created_time DESC LIMIT 1")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            log("读 cookie 失败: " + e.getMessage());
        }
        return null;
    }

    private static List<Cookie> buildPlaywrightCookies(String cookieText) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String part : cookieText.split(";\\s*")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String k = part.substring(0, idx).trim();
                String v = part.substring(idx + 1).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    parsed.put(k, v);
                }
            }
        }
        List<Cookie> result = new ArrayList<>();
        for (Map.Entry<String, String> e : parsed.entrySet()) {
            result.add(new Cookie(e.getKey(), e.getValue()).setDomain(".goofish.com").setPath("/"));
            result.add(new Cookie(e.getKey(), e.getValue()).setDomain(".taobao.com").setPath("/"));
        }
        return result;
    }

    private static boolean hasSlider(Page page) {
        for (String s : List.of("#nc_1_n1z", ".nc-container", ".btn_slide", ".nc_scale")) {
            try {
                if (page.locator(s).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean hasHardBlockText(Page page) {
        for (String t : List.of("验证失败，点击框体重试", "验证失败", "点击框体重试", "框体错误")) {
            try {
                if (page.locator("text=" + t).count() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static void warmupAndNavigate(Page page, String targetUrl) {
        List<String> urls = new ArrayList<>();
        if (!HOMEPAGE_URL.equals(targetUrl)) {
            urls.add(HOMEPAGE_URL);
        }
        urls.add(targetUrl);
        for (String url : urls) {
            try {
                log("warmup/导航 -> " + url);
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(30000));
                page.waitForTimeout(1500);
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE,
                            new Page.WaitForLoadStateOptions().setTimeout(8000));
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                log("导航失败 " + url + ": " + e.getMessage());
            }
        }
        log("当前 URL=" + page.url());
    }

    private static void diagnoseLoginState(Page page) {
        try {
            Object cookieValue = page.evaluate("() => document.cookie");
            String cookieStr = cookieValue == null ? "" : String.valueOf(cookieValue);
            String summary = cookieStr.length() > 300 ? cookieStr.substring(0, 300) + "..." : cookieStr;
            log("document.cookie 长度=" + cookieStr.length() + ", 前300=" + summary);
            boolean hasUnb = cookieStr.contains("unb=");
            boolean hasTk = cookieStr.contains("_m_h5_tk=");
            log("登录态字段：unb=" + hasUnb + ", _m_h5_tk=" + hasTk);
        } catch (Exception e) {
            log("读取 document.cookie 失败: " + e.getMessage());
        }
        try {
            Object value = page.evaluate("""
                    async (url) => {
                        try {
                            const resp = await fetch(url, { credentials: 'include', cache: 'no-store' });
                            const text = await resp.text();
                            return { status: resp.status, text: text.slice(0, 500) };
                        } catch (e) {
                            return { status: -1, text: String((e && e.message) || e || '') };
                        }
                    }
                    """, LOGIN_TOKEN_URL);
            if (value instanceof Map<?, ?> map) {
                Object status = map.get("status");
                String body = String.valueOf(map.get("text"));
                boolean sessionExpired = body.contains("FAIL_SYS_SESSION_EXPIRED");
                boolean userValidate = body.contains("FAIL_SYS_USER_VALIDATE");
                log("mtop login.token: status=" + status
                        + ", SESSION_EXPIRED=" + sessionExpired
                        + ", USER_VALIDATE=" + userValidate
                        + ", body=" + body);
            }
        } catch (Exception e) {
            log("调用 login.token 失败: " + e.getMessage());
        }
    }

    private static String detectChromiumVersion(String chromePath) {
        if (chromePath == null || chromePath.isBlank()) {
            return FALLBACK_VERSION;
        }
        Path path = Path.of(chromePath);
        if (!Files.isExecutable(path)) {
            return FALLBACK_VERSION;
        }
        // Windows Chromium 安装目录下通常有形如 144.0.7559.132/ 的版本子目录，比 --version 更稳
        Path appDir = path.getParent();
        if (appDir != null && Files.isDirectory(appDir)) {
            try (var stream = Files.list(appDir)) {
                String byDir = stream
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.matches("\\d+(?:\\.\\d+){2,3}"))
                        .findFirst()
                        .orElse(null);
                if (byDir != null) {
                    return byDir;
                }
            } catch (Exception ignored) {
            }
        }
        // 回退：尝试 --version stdout（Linux/Mac 上有效，Windows Chrome 不输出到 stdout）
        Process process = null;
        try {
            process = new ProcessBuilder(path.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return FALLBACK_VERSION;
            }
            String text;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                text = reader.lines().reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right);
            }
            Matcher matcher = VERSION_PATTERN.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log("探测 Chromium 版本失败: " + e.getMessage());
        }
        return FALLBACK_VERSION;
    }

    private static String buildUserAgent(String fullVersion) {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + fullVersion + " Safari/537.36";
    }

    private static String secChUa(String fullVersion) {
        String majorVersion = fullVersion.split("\\.", 2)[0];
        return "\"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"" + majorVersion
                + "\", \"Google Chrome\";v=\"" + majorVersion + "\"";
    }

    private static String buildStealthScript(String userAgent, String fullVersion) {
        String majorVersion = fullVersion.split("\\.", 2)[0];
        return String.format("""
                (() => {
                    const defineGetter = (target, key, getter) => {
                        try {
                            Object.defineProperty(target, key, { get: getter, configurable: true });
                        } catch (e) {}
                    };
                    const brands = [
                        { brand: 'Not.A/Brand', version: '8' },
                        { brand: 'Chromium', version: '%s' },
                        { brand: 'Google Chrome', version: '%s' }
                    ];
                    const fullVersionList = [
                        { brand: 'Not.A/Brand', version: '8.0.0.0' },
                        { brand: 'Chromium', version: '%s' },
                        { brand: 'Google Chrome', version: '%s' }
                    ];
                    const uaData = {
                        brands,
                        mobile: false,
                        platform: 'Windows',
                        getHighEntropyValues: async () => ({
                            architecture: 'x86',
                            bitness: '64',
                            brands,
                            fullVersionList,
                            mobile: false,
                            model: '',
                            platform: 'Windows',
                            platformVersion: '10.0.0',
                            uaFullVersion: '%s',
                            wow64: false
                        }),
                        toJSON() { return { brands, mobile: this.mobile, platform: this.platform }; }
                    };
                    defineGetter(Navigator.prototype, 'webdriver', () => false);
                    defineGetter(Navigator.prototype, 'languages', () => ['zh-CN', 'zh', 'en']);
                    defineGetter(Navigator.prototype, 'platform', () => 'Win32');
                    defineGetter(Navigator.prototype, 'vendor', () => 'Google Inc.');
                    defineGetter(Navigator.prototype, 'userAgent', () => '%s');
                    defineGetter(Navigator.prototype, 'hardwareConcurrency', () => 8);
                    defineGetter(Navigator.prototype, 'deviceMemory', () => 8);
                    defineGetter(Navigator.prototype, 'maxTouchPoints', () => 0);
                    defineGetter(Navigator.prototype, 'userAgentData', () => uaData);
                    defineGetter(Navigator.prototype, 'pdfViewerEnabled', () => true);
                    defineGetter(Navigator.prototype, 'plugins', () => [
                        { name: 'PDF Viewer' },
                        { name: 'Chrome PDF Viewer' },
                        { name: 'Chromium PDF Viewer' }
                    ]);
                    if (!window.chrome) window.chrome = {};
                    window.chrome.runtime = window.chrome.runtime || {};
                    window.chrome.app = window.chrome.app || { getDetails: () => null };
                    ['playwright', '__playwright', '__pw_manual', '__pw_original', 'webdriver',
                     '__webdriver_script_fn', '__webdriver_evaluate', '__webdriver_unwrapped']
                        .forEach((key) => { try { delete window[key]; } catch (e) {} });
                })();
                """, majorVersion, majorVersion, fullVersion, fullVersion, fullVersion, userAgent);
    }

    private static void log(String message) {
        System.out.println("[fp-slider " + java.time.LocalTime.now() + "] " + message);
    }
}
