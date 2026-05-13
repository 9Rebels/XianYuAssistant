package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.utils.PlaywrightBrowserUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderBrowserFingerprintServiceTest {

    @Test
    void stealthScriptContainsFullHeadlessPatches() {
        SliderBrowserFingerprintService service = new SliderBrowserFingerprintService();

        String script = service.stealthScript(service.profile(3L));

        assertTrue(script.contains("userAgentData"));
        assertTrue(script.contains("makePluginArray"));
        assertTrue(script.contains("makeMimeTypeArray"));
        assertTrue(script.contains("window.chrome.app"));
        assertTrue(script.contains("window.chrome.csi"));
        assertTrue(script.contains("window.chrome.loadTimes"));
        assertTrue(script.contains("navigator.permissions.query"));
        assertTrue(script.contains("WebGLRenderingContext"));
        assertTrue(script.contains("__playwright"));
        assertTrue(script.contains("__webdriver_evaluate"));
    }

    @Test
    void profileUsesDetectedChromiumVersionForUaAndClientHints() {
        SliderBrowserFingerprintService service = new SliderBrowserFingerprintService(() -> Optional.of(
                new PlaywrightBrowserUtils.ChromiumRuntime(
                        Path.of("/usr/bin/chromium"),
                        "147.0.7727.116",
                        "147",
                        "local_system"
                )
        ));

        SliderBrowserFingerprintService.BrowserProfile profile = service.profile(3L);
        String script = service.stealthScript(profile);
        String secChUa = service.extraHeaders(profile).get("sec-ch-ua");

        assertEquals("win_chrome_147_1600x900", profile.getProfileId());
        assertTrue(profile.getUserAgent().contains("Chrome/147.0.7727.116"));
        assertTrue(secChUa.contains("\"Chromium\";v=\"147\""));
        assertTrue(secChUa.contains("\"Google Chrome\";v=\"147\""));
        assertTrue(script.contains("uaFullVersion"));
        assertTrue(script.contains("147.0.7727.116"));
        assertTrue(script.contains("fullVersionList"));
    }
}
