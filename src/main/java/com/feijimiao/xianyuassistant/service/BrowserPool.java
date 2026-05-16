package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.feijimiao.xianyuassistant.event.account.AccountRemovedEvent;
import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 浏览器实例池管理器
 *
 * 功能：
 * - 按账号ID维护浏览器实例
 * - 复用同一账号的浏览器，减少启动次数
 * - 支持懒加载和自动清理
 * - 超时自动关闭闲置浏览器
 * - 最多维护3个浏览器实例（LRU淘汰策略）
 */
@Slf4j
@Component
public class BrowserPool {

    /**
     * 浏览器实例包装类
     */
    public static class BrowserInstance {
        private final Playwright playwright;
        private final Browser browser;
        private final BrowserContext context;
        private Page page;
        private long lastUsedTime;
        private final boolean headless;

        public BrowserInstance(Playwright playwright, Browser browser, BrowserContext context, Page page) {
            this(playwright, browser, context, page, true);
        }

        public BrowserInstance(Playwright playwright, Browser browser, BrowserContext context, Page page, boolean headless) {
            this.playwright = playwright;
            this.browser = browser;
            this.context = context;
            this.page = page;
            this.lastUsedTime = System.currentTimeMillis();
            this.headless = headless;
        }

        public Playwright getPlaywright() {
            return playwright;
        }

        public Browser getBrowser() {
            return browser;
        }

        public BrowserContext getContext() {
            return context;
        }

        public Page getPage() {
            return page;
        }

        public void setPage(Page page) {
            this.page = page;
        }

        public long getLastUsedTime() {
            return lastUsedTime;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void updateLastUsedTime() {
            this.lastUsedTime = System.currentTimeMillis();
        }

        public boolean isConnected() {
            return browser != null && browser.isConnected();
        }

        public void close() {
            try {
                if (page != null && !page.isClosed()) {
                    page.close();
                }
            } catch (Exception e) {
                log.debug("关闭页面失败: {}", e.getMessage());
            }

            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception e) {
                log.debug("关闭上下文失败: {}", e.getMessage());
            }

            try {
                if (browser != null && browser.isConnected()) {
                    browser.close();
                }
            } catch (Exception e) {
                log.debug("关闭浏览器失败: {}", e.getMessage());
            }

            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception e) {
                log.debug("关闭Playwright失败: {}", e.getMessage());
            }
        }
    }

    // 最大浏览器实例数
    private static final int MAX_SIZE = 3;

    // 闲置超时时间（毫秒），默认5分钟
    private static final long IDLE_TIMEOUT = 5 * 60 * 1000;

    private static final String GOOFISH_COOKIE_DOMAIN = ".goofish.com";
    private static final String TAOBAO_COOKIE_DOMAIN = ".taobao.com";

    private final SliderBrowserFingerprintService sliderBrowserFingerprintService;

    // 浏览器实例池：accountId -> BrowserInstance
    private final ConcurrentHashMap<Long, BrowserInstance> pool = new ConcurrentHashMap<>();

    // 每个账号的锁，防止并发创建
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    // 定时清理任务
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public BrowserPool(SliderBrowserFingerprintService sliderBrowserFingerprintService) {
        this.sliderBrowserFingerprintService = sliderBrowserFingerprintService;
        // 启动定时清理任务，每分钟检查一次
        cleanupExecutor.scheduleAtFixedRate(this::cleanupIdleBrowsers, 1, 1, TimeUnit.MINUTES);
        log.info("浏览器池初始化完成，最大实例数: {}, 闲置超时: {}秒", MAX_SIZE, IDLE_TIMEOUT / 1000);
    }

    /**
     * 获取浏览器实例（如果不存在则创建）
     *
     * @param accountId 账号ID
     * @param cookieText Cookie字符串
     * @param headless 是否无头模式
     * @param createNewPage 是否创建新页面（避免并发冲突）
     * @return BrowserInstance
     */
    public BrowserInstance getBrowser(Long accountId, String cookieText, boolean headless, boolean createNewPage) {
        ReentrantLock lock = locks.computeIfAbsent(accountId, k -> new ReentrantLock());
        lock.lock();
        try {
            // 检查是否已存在该账号的浏览器实例
            BrowserInstance instance = pool.get(accountId);
            if (instance != null && instance.isConnected()) {
                if (instance.isHeadless() != headless) {
                    log.info("浏览器实例模式不匹配，关闭后重建: accountId={}, existingHeadless={}, requestedHeadless={}",
                        accountId, instance.isHeadless(), headless);
                    closeBrowserUnsafe(accountId);
                    instance = null;
                }
            }
            BrowserInstance reusable = reuseExistingBrowserUnsafe(accountId, createNewPage);
            if (reusable != null) {
                return reusable;
            }

            // 如果池已满，清理最旧的实例
            ensurePoolSize();

            // 创建新的浏览器实例
            log.info("创建新的浏览器实例: accountId={}, headless={}", accountId, headless);
            instance = createBrowser(accountId, cookieText, headless);
            if (instance != null) {
                pool.put(accountId, instance);
                log.info("浏览器实例创建成功: accountId={}, 当前池大小: {}", accountId, pool.size());
            } else {
                log.error("浏览器实例创建失败: accountId={}", accountId);
            }
            return instance;
        } finally {
            lock.unlock();
        }
    }

    public BrowserInstance getExistingBrowser(Long accountId, boolean createNewPage) {
        ReentrantLock lock = locks.computeIfAbsent(accountId, k -> new ReentrantLock());
        lock.lock();
        try {
            return reuseExistingBrowserUnsafe(accountId, createNewPage);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 创建新的浏览器实例
     */
    private BrowserInstance createBrowser(Long accountId, String cookieText, boolean headless) {
        return createLocalBrowser(accountId, cookieText, headless);
    }

    private BrowserInstance reuseExistingBrowserUnsafe(Long accountId, boolean createNewPage) {
        BrowserInstance instance = pool.get(accountId);
        if (instance == null || !instance.isConnected()) {
            return null;
        }
        try {
            if (instance.getContext().pages().isEmpty()) {
                log.warn("浏览器上下文无可用页面，关闭实例: accountId={}", accountId);
                closeBrowserUnsafe(accountId);
                return null;
            }
            instance.updateLastUsedTime();
            if (!createNewPage) {
                log.info("复用已存在的浏览器实例: accountId={}", accountId);
                return instance;
            }
            Page newPage = instance.getContext().newPage();
            log.info("复用浏览器实例并创建新页面: accountId={}", accountId);
            return new BrowserInstance(
                instance.getPlaywright(),
                instance.getBrowser(),
                instance.getContext(),
                newPage,
                instance.isHeadless()
            );
        } catch (Exception e) {
            log.warn("浏览器实例不可用: accountId={}, error={}", accountId, e.getMessage());
            closeBrowserUnsafe(accountId);
            return null;
        }
    }

    /**
     * 在本地创建浏览器实例
     */
    private BrowserInstance createLocalBrowser(Long accountId, String cookieText, boolean headless) {
        try {
            SliderBrowserFingerprintService.BrowserProfile profile =
                    sliderBrowserFingerprintService.profile(accountId);
            List<String> args = new ArrayList<>(sliderBrowserFingerprintService.buildLaunchArgs(profile));
            if (headless) {
                args.add("--disable-gpu");
            } else {
                args.add("--start-maximized");
            }
            args.add("--remote-allow-origins=*");

            // 优先使用 connectOverCDP 绕过 Playwright Runtime.Enable 检测
            ExternalBrowserLauncher launcher = null;
            Playwright playwright = Playwright.create();
            Browser browser;
            try {
                Path profileDir = Path.of(System.getProperty("user.dir"), "browser_data", "user_" + accountId);
                java.nio.file.Files.createDirectories(profileDir);
                launcher = ExternalBrowserLauncher.launch(args, profileDir, accountId);
                browser = playwright.chromium().connectOverCDP(launcher.getCdpUrl());
                log.info("BrowserPool 使用 connectOverCDP 创建浏览器: accountId={}", accountId);
            } catch (Exception e) {
                log.warn("BrowserPool connectOverCDP 失败，回退 launch: accountId={}, error={}",
                        accountId, e.getMessage());
                if (launcher != null) {
                    launcher.shutdown();
                    launcher = null;
                }
                BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                        .setHeadless(headless)
                        .setIgnoreDefaultArgs(List.of("--enable-automation"))
                        .setArgs(args);
                launchOptions.setExecutablePath(PlaywrightBrowserUtils.resolveChromiumPath().orElseThrow());
                browser = playwright.chromium().launch(launchOptions);
            }

            List<Cookie> browserCookies = buildBrowserCookies(cookieText);

            BrowserContext context;
            if (launcher != null && !browser.contexts().isEmpty()) {
                context = browser.contexts().get(0);
            } else {
                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setViewportSize(profile.getViewportWidth(), profile.getViewportHeight())
                    .setScreenSize(profile.getViewportWidth(), profile.getViewportHeight())
                    .setDeviceScaleFactor(profile.getDeviceScaleFactor())
                    .setIsMobile(false)
                    .setHasTouch(false)
                    .setUserAgent(profile.getUserAgent())
                    .setLocale(profile.getLocale())
                    .setTimezoneId(profile.getTimezoneId())
                    .setExtraHTTPHeaders(sliderBrowserFingerprintService.extraHeaders(profile));
                context = browser.newContext(contextOptions);
            }

            // 注入反检测脚本（与滑块链路同源，由 fingerprint-chromium 底层指纹叠加 JS 层兜底）
            String stealthScript = sliderBrowserFingerprintService.stealthScript(profile);
            if (stealthScript != null && !stealthScript.isBlank()) {
                context.addInitScript(stealthScript);
            }

            // 在创建页面前注入Cookie
            if (!browserCookies.isEmpty()) {
                context.addCookies(browserCookies);
                log.info("已设置 {} 个浏览器 Cookie", browserCookies.size());
            }

            Page page = context.newPage();
            if (launcher == null) {
                sliderBrowserFingerprintService.applyNetworkFingerprint(context, page, profile);
            }
            BrowserInstance browserInstance = new BrowserInstance(playwright, browser, context, page, headless);
            log.info("验证码浏览器创建成功: accountId={}, profile={}, ua={}",
                    accountId, profile.getProfileId(), profile.getUserAgent());
            return browserInstance;
        } catch (Exception e) {
            log.error("创建浏览器实例失败: accountId={}", accountId, e);
            return null;
        }
    }

    private List<Cookie> buildBrowserCookies(String cookieText) {
        List<Cookie> cookies = new ArrayList<>();
        if (cookieText == null || cookieText.isBlank()) {
            log.warn("Cookie文本为空，无法注入Cookie");
            return cookies;
        }
        String[] cookiePairs = cookieText.split(";\\s*");
        log.info("开始解析Cookie，共 {} 对", cookiePairs.length);

        for (String pair : cookiePairs) {
            if (!pair.contains("=")) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String name = parts[0].trim();
            String value = parts[1].trim();
            if (name.isBlank() || value.isBlank()) {
                continue;
            }

            // 为goofish.com和taobao.com各添加一份Cookie
            Cookie goofishCookie = new Cookie(name, value)
                .setDomain(GOOFISH_COOKIE_DOMAIN)
                .setPath("/");
            Cookie taobaoCookie = new Cookie(name, value)
                .setDomain(TAOBAO_COOKIE_DOMAIN)
                .setPath("/");

            cookies.add(goofishCookie);
            cookies.add(taobaoCookie);
        }

        log.info("Cookie解析完成，生成 {} 个浏览器Cookie（包含goofish和taobao域）", cookies.size());
        return cookies;
    }

    /**
     * 确保池大小不超过最大限制
     */
    private void ensurePoolSize() {
        while (pool.size() >= MAX_SIZE) {
            // 找到最旧的实例（LRU淘汰）
            Long oldestAccountId = null;
            long oldestTime = Long.MAX_VALUE;

            for (Map.Entry<Long, BrowserInstance> entry : pool.entrySet()) {
                if (entry.getValue().getLastUsedTime() < oldestTime) {
                    oldestTime = entry.getValue().getLastUsedTime();
                    oldestAccountId = entry.getKey();
                }
            }

            if (oldestAccountId != null) {
                log.info("浏览器池已满，清理最旧的实例: accountId={}", oldestAccountId);
                closeBrowserUnsafe(oldestAccountId);
            } else {
                break;
            }
        }
    }

    /**
     * 清理闲置的浏览器实例
     */
    private void cleanupIdleBrowsers() {
        long currentTime = System.currentTimeMillis();
        pool.forEach((accountId, instance) -> {
            if (currentTime - instance.getLastUsedTime() > IDLE_TIMEOUT) {
                log.info("清理闲置的浏览器实例: accountId={}, 闲置时间: {}秒",
                    accountId, (currentTime - instance.getLastUsedTime()) / 1000);
                closeBrowser(accountId);
            }
        });
    }

    /**
     * 关闭指定账号的浏览器实例
     */
    public void closeBrowser(Long accountId) {
        ReentrantLock lock = locks.get(accountId);
        if (lock != null) {
            lock.lock();
            try {
                closeBrowserUnsafe(accountId);
            } finally {
                lock.unlock();
            }
        } else {
            closeBrowserUnsafe(accountId);
        }
    }

    /**
     * 关闭浏览器实例（不加锁，内部使用）
     */
    private void closeBrowserUnsafe(Long accountId) {
        BrowserInstance instance = pool.remove(accountId);
        if (instance != null) {
            instance.close();
            log.info("浏览器实例已关闭: accountId={}", accountId);
        }
    }

    /**
     * 账号删除事件处理：关闭对应浏览器实例并释放账号级锁
     */
    @EventListener
    public void onAccountRemoved(AccountRemovedEvent event) {
        Long accountId = event.getAccountId();
        try {
            log.info("【账号{}】收到账号删除事件，关闭浏览器实例并释放账号锁", accountId);
            closeBrowser(accountId);
            locks.remove(accountId);
        } catch (Exception e) {
            log.warn("【账号{}】浏览器池账号删除事件处理异常: {}", accountId, e.getMessage(), e);
        }
    }

    /**
     * 关闭所有浏览器实例
     */
    public void closeAll() {
        log.info("关闭所有浏览器实例，当前池大小: {}", pool.size());
        pool.forEach((accountId, instance) -> {
            try {
                instance.close();
            } catch (Exception e) {
                log.error("关闭浏览器实例失败: accountId={}", accountId, e);
            }
        });
        pool.clear();
        log.info("所有浏览器实例已关闭");
    }

    /**
     * 获取池状态信息
     */
    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("total", pool.size());
        status.put("maxSize", MAX_SIZE);
        status.put("idleTimeout", IDLE_TIMEOUT / 1000);

        Map<Long, Map<String, Object>> instances = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        pool.forEach((accountId, instance) -> {
            Map<String, Object> info = new HashMap<>();
            info.put("connected", instance.isConnected());
            info.put("idleTime", (currentTime - instance.getLastUsedTime()) / 1000);
            info.put("lastUsed", instance.getLastUsedTime());
            info.put("headless", instance.isHeadless());
            instances.put(accountId, info);
        });
        status.put("instances", instances);

        return status;
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void destroy() {
        log.info("应用关闭，清理浏览器池资源");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closeAll();
    }
}
