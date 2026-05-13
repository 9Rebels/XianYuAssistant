package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.config.SliderProperties;
import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
public class SliderBrowserFingerprintService {
    private static final Gson GSON = new Gson();
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8";
    private static final String FALLBACK_CHROME_VERSION = "120.0.0.0";
    private static final String FALLBACK_CHROME_MAJOR = "120";
    static final String STEALTH_MODE_OFF = "off";
    static final String STEALTH_MODE_LITE = "lite";
    static final String STEALTH_MODE_FULL = "full";

    private final Supplier<Optional<PlaywrightBrowserUtils.ChromiumRuntime>> runtimeSupplier;
    private SliderProperties sliderProperties;

    public SliderBrowserFingerprintService() {
        this(PlaywrightBrowserUtils::detectChromiumRuntime);
    }

    SliderBrowserFingerprintService(Supplier<Optional<PlaywrightBrowserUtils.ChromiumRuntime>> runtimeSupplier) {
        this.runtimeSupplier = runtimeSupplier;
    }

    @Autowired(required = false)
    void setSliderProperties(SliderProperties sliderProperties) {
        this.sliderProperties = sliderProperties;
    }

    BrowserProfile profile(Long accountId) {
        RuntimeVersion runtime = resolveRuntimeVersion();
        int viewportWidth = 1600;
        int viewportHeight = 900;
        log.info("滑块浏览器指纹版本: profile=win_chrome_{}_{}x{}, chromeVersion={}, source={}, path={}",
                runtime.majorVersion(),
                viewportWidth,
                viewportHeight,
                runtime.fullVersion(),
                runtime.source(),
                runtime.path());
        return new BrowserProfile(
                "win_chrome_" + runtime.majorVersion() + "_" + viewportWidth + "x" + viewportHeight,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/" + runtime.fullVersion() + " Safari/537.36",
                runtime.fullVersion(),
                runtime.majorVersion(),
                "Win32",
                "Windows",
                "Google Inc.",
                "zh-CN",
                ACCEPT_LANGUAGE,
                "Asia/Shanghai",
                viewportWidth,
                viewportHeight,
                1D,
                8 + stableInt(accountId, "cpu", 0, 2) * 2,
                8,
                24,
                5 + stableInt(accountId, "plugins", 0, 2),
                stableInt(accountId, "notification", 0, 1) == 0 ? "default" : "denied",
                stableInt(accountId, "dnt", 0, 2) == 0 ? "0" : "unspecified",
                30 + stableInt(accountId, "rtt", 0, 40),
                4.5D + stableInt(accountId, "downlink", 0, 45) / 10D
        );
    }

    Map<String, String> extraHeaders(BrowserProfile profile) {
        return Map.of(
                "Accept-Language", profile.getAcceptLanguage(),
                "sec-ch-ua", secChUa(profile),
                "sec-ch-ua-mobile", "?0",
                "sec-ch-ua-platform", "\"Windows\""
        );
    }

    private RuntimeVersion resolveRuntimeVersion() {
        Optional<PlaywrightBrowserUtils.ChromiumRuntime> runtime = Optional.empty();
        try {
            runtime = runtimeSupplier.get();
        } catch (Exception e) {
            log.debug("探测滑块浏览器版本失败: {}", e.getMessage());
        }
        if (runtime.isPresent() && hasText(runtime.get().fullVersion()) && hasText(runtime.get().majorVersion())) {
            return new RuntimeVersion(
                    runtime.get().fullVersion(),
                    runtime.get().majorVersion(),
                    String.valueOf(runtime.get().source()),
                    String.valueOf(runtime.get().executablePath())
            );
        }
        return new RuntimeVersion(FALLBACK_CHROME_VERSION, FALLBACK_CHROME_MAJOR, "fallback", "");
    }

    void applyNetworkFingerprint(BrowserContext context, Page page, BrowserProfile profile) {
        if (context == null || page == null || page.isClosed()) {
            return;
        }
        CDPSession session = null;
        try {
            session = context.newCDPSession(page);
            session.send("Network.enable");
            session.send("Network.setUserAgentOverride", networkOverridePayload(profile));
            log.info("滑块浏览器已应用 UA/Client-Hints 网络层伪装: profile={}", profile.getProfileId());
        } catch (Exception e) {
            log.debug("应用无头网络指纹失败: {}", e.getMessage());
        } finally {
            if (session != null) {
                try {
                    session.detach();
                } catch (Exception ignored) {
                }
            }
        }
    }

    String stealthScript(BrowserProfile profile) {
        String mode = resolveStealthMode();
        if (STEALTH_MODE_OFF.equals(mode)) {
            log.info("滑块 stealth 已关闭: profile={}", profile.getProfileId());
            return "";
        }
        if (STEALTH_MODE_LITE.equals(mode)) {
            log.info("滑块 stealth 使用 lite 模式: profile={}", profile.getProfileId());
            return liteStealthScript(profile);
        }
        return fullStealthScript(profile);
    }

    String resolveStealthMode() {
        if (sliderProperties == null || sliderProperties.getStealth() == null) {
            return STEALTH_MODE_FULL;
        }
        String mode = sliderProperties.getStealth().getMode();
        if (mode == null) {
            return STEALTH_MODE_FULL;
        }
        String normalized = mode.trim().toLowerCase();
        return switch (normalized) {
            case STEALTH_MODE_OFF, STEALTH_MODE_LITE, STEALTH_MODE_FULL -> normalized;
            default -> {
                log.warn("未知 slider.stealth.mode={}，回退 full", mode);
                yield STEALTH_MODE_FULL;
            }
        };
    }

    private String liteStealthScript(BrowserProfile profile) {
        return """
                (() => {
                    const defineGetter = (target, key, getter) => {
                        try {
                            Object.defineProperty(target, key, { get: getter, configurable: true });
                        } catch (e) {}
                    };
                    const uaData = {
                        brands: %s,
                        mobile: false,
                        platform: 'Windows',
                        toJSON() {
                            return { brands: this.brands, mobile: this.mobile, platform: this.platform };
                        }
                    };
                    defineGetter(Navigator.prototype, 'webdriver', () => false);
                    defineGetter(Navigator.prototype, 'languages', () => [%s, 'zh', 'en']);
                    defineGetter(Navigator.prototype, 'platform', () => %s);
                    defineGetter(Navigator.prototype, 'userAgent', () => %s);
                    defineGetter(Navigator.prototype, 'userAgentData', () => uaData);
                    [
                        'playwright', '__playwright', '__pw_manual', '__pw_original', 'webdriver',
                        '__webdriver_script_fn', '__webdriver_evaluate', '__webdriver_unwrapped'
                    ].forEach((key) => {
                        try { delete window[key]; } catch (e) {}
                    });
                })();
                """.formatted(
                GSON.toJson(brands(profile)),
                GSON.toJson(profile.getLocale()),
                GSON.toJson(profile.getNavigatorPlatform()),
                GSON.toJson(profile.getUserAgent())
        );
    }

    private String fullStealthScript(BrowserProfile profile) {
        return """
                (() => {
                    const defineGetter = (target, key, getter) => {
                        try {
                            Object.defineProperty(target, key, { get: getter, configurable: true });
                        } catch (e) {}
                    };
                    const languages = [%s, 'zh', 'en'];
                    const mimeTypes = [
                        { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
                        { type: 'text/pdf', suffixes: 'pdf', description: 'Portable Document Format' }
                    ];
                    const pluginNames = [
                        'PDF Viewer',
                        'Chrome PDF Viewer',
                        'Chromium PDF Viewer',
                        'Microsoft Edge PDF Viewer',
                        'WebKit built-in PDF'
                    ].slice(0, %d);
                    const makeMimeTypeArray = () => {
                        const arr = mimeTypes.map((item) => Object.assign({}, item));
                        arr.item = (i) => arr[i] || null;
                        arr.namedItem = (name) => arr.find(p => p.type === name) || null;
                        arr.refresh = () => undefined;
                        return arr;
                    };
                    const makePluginArray = () => {
                        const firstMime = mimeTypes[0];
                        const arr = pluginNames.map((name) => ({
                            name,
                            filename: name.toLowerCase().replace(/\\s+/g, '-') + '.dll',
                            description: name,
                            length: 1,
                            0: firstMime,
                            item: (i) => i === 0 ? firstMime : null,
                            namedItem: (type) => type === firstMime.type ? firstMime : null
                        }));
                        arr.item = (i) => arr[i] || null;
                        arr.namedItem = (name) => arr.find(p => p.name === name) || null;
                        arr.refresh = () => undefined;
                        return arr;
                    };
                    const uaData = {
                        brands: %s,
                        mobile: false,
                        platform: 'Windows',
                        getHighEntropyValues: async (hints) => {
                            const payload = {
                                architecture: 'x86',
                                bitness: '64',
                                brands: %s,
                                fullVersionList: %s,
                                mobile: false,
                                model: '',
                                platform: 'Windows',
                                platformVersion: '10.0.0',
                                uaFullVersion: %s,
                                wow64: false
                            };
                            if (!Array.isArray(hints) || hints.length === 0) return payload;
                            return hints.reduce((acc, key) => {
                                if (Object.prototype.hasOwnProperty.call(payload, key)) acc[key] = payload[key];
                                return acc;
                            }, {});
                        },
                        toJSON() {
                            return { brands: this.brands, mobile: this.mobile, platform: this.platform };
                        }
                    };
                    defineGetter(Navigator.prototype, 'webdriver', () => false);
                    defineGetter(Navigator.prototype, 'languages', () => languages);
                    defineGetter(Navigator.prototype, 'plugins', () => makePluginArray());
                    defineGetter(Navigator.prototype, 'mimeTypes', () => makeMimeTypeArray());
                    defineGetter(Navigator.prototype, 'platform', () => %s);
                    defineGetter(Navigator.prototype, 'vendor', () => %s);
                    defineGetter(Navigator.prototype, 'userAgent', () => %s);
                    defineGetter(Navigator.prototype, 'hardwareConcurrency', () => %d);
                    defineGetter(Navigator.prototype, 'deviceMemory', () => %d);
                    defineGetter(Navigator.prototype, 'maxTouchPoints', () => 0);
                    defineGetter(Navigator.prototype, 'userAgentData', () => uaData);
                    defineGetter(Navigator.prototype, 'pdfViewerEnabled', () => true);
                    defineGetter(Navigator.prototype, 'doNotTrack', () => %s);
                    defineGetter(window, 'outerWidth', () => %d);
                    defineGetter(window, 'outerHeight', () => %d);
                    defineGetter(screen, 'width', () => %d);
                    defineGetter(screen, 'height', () => %d);
                    defineGetter(screen, 'availWidth', () => %d);
                    defineGetter(screen, 'availHeight', () => %d);
                    defineGetter(screen, 'colorDepth', () => %d);
                    defineGetter(screen, 'pixelDepth', () => %d);
                    defineGetter(Navigator.prototype, 'connection', () => ({
                        effectiveType: '4g',
                        rtt: %d,
                        downlink: %s,
                        saveData: false
                    }));
                    if (!window.chrome) window.chrome = {};
                    window.chrome.runtime = window.chrome.runtime || {};
                    window.chrome.app = window.chrome.app || {
                        InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' },
                        RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' },
                        getDetails: () => null,
                        getIsInstalled: () => false,
                        runningState: () => 'cannot_run'
                    };
                    window.chrome.csi = window.chrome.csi || (() => ({}));
                    window.chrome.loadTimes = window.chrome.loadTimes || (() => ({}));
                    const originalQuery = window.navigator.permissions && window.navigator.permissions.query;
                    if (originalQuery) {
                        window.navigator.permissions.query = (parameters) => {
                            const name = parameters && parameters.name;
                            if (name === 'notifications') {
                                return Promise.resolve({ state: %s, onchange: null });
                            }
                            return originalQuery.call(window.navigator.permissions, parameters);
                        };
                    }
                    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
                        navigator.mediaDevices.enumerateDevices = async () => ([
                            { deviceId: 'default', kind: 'audioinput', label: '', groupId: 'default' },
                            { deviceId: 'default', kind: 'audiooutput', label: '', groupId: 'default' }
                        ]);
                    }
                    const patchWebGL = (prototype) => {
                        if (!prototype || !prototype.getParameter) return;
                        const originalGetParameter = prototype.getParameter;
                        prototype.getParameter = function(parameter) {
                            if (parameter === 37445) return 'Google Inc. (Intel)';
                            if (parameter === 37446) return 'ANGLE (Intel, Intel(R) UHD Graphics Direct3D11 vs_5_0 ps_5_0, D3D11)';
                            return originalGetParameter.call(this, parameter);
                        };
                    };
                    patchWebGL(window.WebGLRenderingContext && WebGLRenderingContext.prototype);
                    patchWebGL(window.WebGL2RenderingContext && WebGL2RenderingContext.prototype);
                    const originalToString = Function.prototype.toString;
                    Function.prototype.toString = function() {
                        if (this === window.navigator.permissions.query) return 'function query() { [native code] }';
                        return originalToString.call(this);
                    };
                    [
                        'playwright', '__playwright', '__pw_manual', '__pw_original', 'webdriver',
                        '__webdriver_script_fn', '__webdriver_evaluate', '__webdriver_unwrapped',
                        '__fxdriver_evaluate', '__driver_evaluate', '__webdriver_script_func',
                        '_selenium', '_phantom', 'callPhantom', 'phantom',
                        '__playwright_evaluation_script__', '__pw_d'
                    ].forEach((key) => {
                        try { delete window[key]; } catch (e) {}
                    });
                    // CDP leak: intercept sourceURL markers from addScriptToEvaluateOnNewDocument
                    try {
                        const origError = Error;
                        const origPrepare = Error.prepareStackTrace;
                        Error.prepareStackTrace = function(err, stack) {
                            const filtered = stack.filter(f => {
                                const fn = f.getFileName() || '';
                                return !fn.includes('__playwright') && !fn.includes('pptr:');
                            });
                            if (origPrepare) return origPrepare(err, filtered);
                            return filtered.map(f => '    at ' + f.toString()).join('\\n');
                        };
                    } catch (e) {}
                    // WebRTC IP leak protection
                    try {
                        if (window.RTCPeerConnection) {
                            const origRTC = window.RTCPeerConnection;
                            window.RTCPeerConnection = function(config, constraints) {
                                if (config && config.iceServers) {
                                    config.iceServers = [];
                                }
                                return new origRTC(config, constraints);
                            };
                            window.RTCPeerConnection.prototype = origRTC.prototype;
                            Object.defineProperty(window.RTCPeerConnection, 'name', { value: 'RTCPeerConnection' });
                        }
                    } catch (e) {}
                    // Notification.permission consistency
                    try {
                        Object.defineProperty(Notification, 'permission', {
                            get: () => %s,
                            configurable: true
                        });
                    } catch (e) {}
                })();
                """.formatted(
                GSON.toJson(profile.getLocale()),
                profile.getPluginCount(),
                GSON.toJson(brands(profile)),
                GSON.toJson(brands(profile)),
                GSON.toJson(fullVersionList(profile)),
                GSON.toJson(profile.getFullVersion()),
                GSON.toJson(profile.getNavigatorPlatform()),
                GSON.toJson(profile.getVendor()),
                GSON.toJson(profile.getUserAgent()),
                profile.getHardwareConcurrency(),
                profile.getDeviceMemory(),
                GSON.toJson(profile.getDoNotTrack()),
                profile.getViewportWidth(),
                profile.getViewportHeight() + 88,
                profile.getViewportWidth(),
                profile.getViewportHeight(),
                profile.getViewportWidth(),
                profile.getViewportHeight() - 40,
                profile.getColorDepth(),
                profile.getColorDepth(),
                profile.getConnectionRtt(),
                Double.toString(profile.getConnectionDownlink()),
                GSON.toJson(profile.getNotificationPermission()),
                GSON.toJson(profile.getNotificationPermission())
        );
    }

    private JsonObject networkOverridePayload(BrowserProfile profile) {
        JsonObject payload = new JsonObject();
        payload.addProperty("userAgent", profile.getUserAgent());
        payload.addProperty("acceptLanguage", profile.getAcceptLanguage());
        payload.addProperty("platform", profile.getUaPlatform());
        JsonObject metadata = new JsonObject();
        metadata.add("brands", brandsArray(profile));
        metadata.add("fullVersionList", fullVersionListArray(profile));
        metadata.addProperty("fullVersion", profile.getFullVersion());
        metadata.addProperty("platform", "Windows");
        metadata.addProperty("platformVersion", "10.0.0");
        metadata.addProperty("architecture", "x86");
        metadata.addProperty("bitness", "64");
        metadata.addProperty("model", "");
        metadata.addProperty("mobile", false);
        metadata.addProperty("wow64", false);
        payload.add("userAgentMetadata", metadata);
        return payload;
    }

    private String secChUa(BrowserProfile profile) {
        return "\"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"" + profile.getMajorVersion()
                + "\", \"Google Chrome\";v=\"" + profile.getMajorVersion() + "\"";
    }

    private List<Map<String, String>> brands(BrowserProfile profile) {
        return List.of(
                Map.of("brand", "Not.A/Brand", "version", "8"),
                Map.of("brand", "Chromium", "version", profile.getMajorVersion()),
                Map.of("brand", "Google Chrome", "version", profile.getMajorVersion())
        );
    }

    private List<Map<String, String>> fullVersionList(BrowserProfile profile) {
        return List.of(
                Map.of("brand", "Not.A/Brand", "version", "8.0.0.0"),
                Map.of("brand", "Chromium", "version", profile.getFullVersion()),
                Map.of("brand", "Google Chrome", "version", profile.getFullVersion())
        );
    }

    private JsonArray brandsArray(BrowserProfile profile) {
        return GSON.toJsonTree(brands(profile)).getAsJsonArray();
    }

    private JsonArray fullVersionListArray(BrowserProfile profile) {
        return GSON.toJsonTree(fullVersionList(profile)).getAsJsonArray();
    }

    private int stableInt(Long accountId, String namespace, int min, int max) {
        int range = max - min + 1;
        if (range <= 0) {
            return min;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((accountId + ":" + namespace).getBytes(StandardCharsets.UTF_8));
            int value = ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
            return min + Math.floorMod(value, range);
        } catch (Exception e) {
            return min;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RuntimeVersion(String fullVersion, String majorVersion, String source, String path) {
    }

    @Value
    static class BrowserProfile {
        String profileId;
        String userAgent;
        String fullVersion;
        String majorVersion;
        String navigatorPlatform;
        String uaPlatform;
        String vendor;
        String locale;
        String acceptLanguage;
        String timezoneId;
        int viewportWidth;
        int viewportHeight;
        double deviceScaleFactor;
        int hardwareConcurrency;
        int deviceMemory;
        int colorDepth;
        int pluginCount;
        String notificationPermission;
        String doNotTrack;
        int connectionRtt;
        double connectionDownlink;
    }
}
