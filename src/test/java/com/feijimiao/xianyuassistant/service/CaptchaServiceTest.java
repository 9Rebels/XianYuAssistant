package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceTest {
    private XianyuSliderStealthService xianyuSliderStealthService;

    @BeforeEach
    void setUp() {
        xianyuSliderStealthService = mock(XianyuSliderStealthService.class);
    }

    @Test
    void handleCaptchaReturnsAutoSuccessWhenPasswordLoginSucceeds() {
        CaptchaService service = new CaptchaService();
        BrowserPool browserPool = mock(BrowserPool.class);
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        Page page = mockCaptchaPage("验证", "https://www.goofish.com/im", true);
        BrowserPool.BrowserInstance browserInstance = browserInstance(page, true);

        ReflectionTestUtils.setField(service, "browserPool", browserPool);
        ReflectionTestUtils.setField(service, "xianyuSliderStealthService", xianyuSliderStealthService);
        ReflectionTestUtils.setField(service, "passwordLoginService", passwordLoginService);
        when(browserPool.getExistingBrowser(1L, true)).thenReturn(browserInstance);
        when(passwordLoginService.tryPasswordLogin(1L)).thenReturn("unb=abc; new-cookie=value");

        CaptchaService.CaptchaResult result =
                service.handleCaptcha(1L, "cookie", "https://www.goofish.com/im", true);

        // 跳过自动滑块，直接走账密登录
        verify(xianyuSliderStealthService, never()).verify(1L, "cookie", "https://www.goofish.com/im", false);
        verify(passwordLoginService).tryPasswordLogin(1L);
        assertTrue(result.isNeedCaptcha());
        assertTrue(result.isAutoVerifySuccess());
        assertEquals("自动验证成功", result.getMessage());
    }

    @Test
    void handleCaptchaReturnsCookieUpdateMessageWhenAllRecoveryFails() {
        CaptchaService service = new CaptchaService();
        BrowserPool browserPool = mock(BrowserPool.class);
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        Page page = mockCaptchaPage("验证", "https://www.goofish.com/im", true);
        BrowserPool.BrowserInstance browserInstance = browserInstance(page, true);

        ReflectionTestUtils.setField(service, "browserPool", browserPool);
        ReflectionTestUtils.setField(service, "xianyuSliderStealthService", xianyuSliderStealthService);
        ReflectionTestUtils.setField(service, "passwordLoginService", passwordLoginService);
        when(browserPool.getExistingBrowser(1L, true)).thenReturn(browserInstance);
        when(passwordLoginService.tryPasswordLogin(1L)).thenReturn(null);
        when(xianyuSliderStealthService.verify(1L, "cookie", "https://www.goofish.com/im", true))
                .thenReturn(XianyuSliderStealthService.SliderVerificationResult.failed("人工恢复失败"));

        CaptchaService.CaptchaResult result =
                service.handleCaptcha(1L, "cookie", "https://www.goofish.com/im", true);

        // 跳过自动滑块，账密登录失败后走人工兜底
        verify(xianyuSliderStealthService, never()).verify(1L, "cookie", "https://www.goofish.com/im", false);
        verify(passwordLoginService).tryPasswordLogin(1L);
        verify(xianyuSliderStealthService).verify(1L, "cookie", "https://www.goofish.com/im", true);
        assertTrue(result.isNeedCaptcha());
        assertFalse(result.isAutoVerifySuccess());
        assertEquals(CaptchaService.AUTO_SLIDER_FAILED_MESSAGE, result.getMessage());
        assertNull(result.getCookieText());
    }

    @Test
    void handleCaptchaReturnsNoCaptchaWhenPageHasNoSlider() {
        CaptchaService service = new CaptchaService();
        BrowserPool browserPool = mock(BrowserPool.class);
        Page page = mockCaptchaPage("闲鱼", "https://www.goofish.com/im", false);
        BrowserPool.BrowserInstance browserInstance = browserInstance(page, true);

        ReflectionTestUtils.setField(service, "browserPool", browserPool);
        ReflectionTestUtils.setField(service, "xianyuSliderStealthService", xianyuSliderStealthService);
        when(browserPool.getExistingBrowser(1L, true)).thenReturn(browserInstance);

        CaptchaService.CaptchaResult result =
                service.handleCaptcha(1L, "cookie", "https://www.goofish.com/im", true);

        assertFalse(result.isNeedCaptcha());
        assertEquals("无需验证", result.getMessage());
    }

    @Test
    void handleCaptchaTreatsPunishUrlAsCaptchaWhenPageMarkersAreMissing() {
        CaptchaService service = new CaptchaService();
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        String captchaUrl = "https://h5api.m.goofish.com/h5/mtop.xxx/punish?x5secdata=abc&x5step=2&action=captcha";
        ReflectionTestUtils.setField(service, "xianyuSliderStealthService", xianyuSliderStealthService);
        ReflectionTestUtils.setField(service, "passwordLoginService", passwordLoginService);
        when(passwordLoginService.tryPasswordLogin(1L)).thenReturn(null);
        when(xianyuSliderStealthService.verify(1L, "cookie", captchaUrl, true))
                .thenReturn(XianyuSliderStealthService.SliderVerificationResult.failed("人工恢复失败"));

        CaptchaService.CaptchaResult result = service.handleCaptcha(
                1L, "cookie", captchaUrl, true);

        // 跳过自动滑块，账密登录失败后走人工兜底
        verify(xianyuSliderStealthService, never()).verify(1L, "cookie", captchaUrl, false);
        verify(passwordLoginService).tryPasswordLogin(1L);
        assertTrue(result.isNeedCaptcha());
        assertFalse(result.isAutoVerifySuccess());
        assertEquals(CaptchaService.AUTO_SLIDER_FAILED_MESSAGE, result.getMessage());
    }

    @Test
    void handleCaptchaReturnsPasswordLoginCookieForKnownCaptchaUrl() {
        CaptchaService service = new CaptchaService();
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        String captchaUrl = "https://h5api.m.goofish.com/h5/mtop.xxx/punish?x5secdata=abc&x5step=2&action=captcha";
        ReflectionTestUtils.setField(service, "xianyuSliderStealthService", xianyuSliderStealthService);
        ReflectionTestUtils.setField(service, "passwordLoginService", passwordLoginService);
        when(passwordLoginService.tryPasswordLogin(1L)).thenReturn(
                "unb=user-1; sgcookie=sg-new; _m_h5_tk=tk-new; x5secdata=x5-new");

        CaptchaService.CaptchaResult result = service.handleCaptcha(
                1L,
                "unb=user-1; sgcookie=sg-old; _m_h5_tk=tk-old",
                captchaUrl,
                true
        );

        assertTrue(result.isAutoVerifySuccess());
        assertTrue(result.getCookieText().contains("_m_h5_tk=tk-new"));
        assertTrue(result.getCookieText().contains("x5secdata=x5-new"));
    }

    @Test
    void handleCaptchaTriesPasswordLoginBeforeManualRecovery() {
        CaptchaService service = new CaptchaService();
        PasswordLoginService passwordLoginService = mock(PasswordLoginService.class);
        String captchaUrl =
                "https://h5api.m.goofish.com/h5/mtop.xxx/punish?x5secdata=abc&x5step=2&action=captcha";
        ReflectionTestUtils.setField(service, "xianyuSliderStealthService", xianyuSliderStealthService);
        ReflectionTestUtils.setField(service, "passwordLoginService", passwordLoginService);
        when(passwordLoginService.tryPasswordLogin(1L)).thenReturn("unb=abc; password-cookie=value");

        CaptchaService.CaptchaResult result = service.handleCaptcha(
                1L, "cookie", captchaUrl, true);

        assertTrue(result.isAutoVerifySuccess());
        assertEquals("unb=abc; password-cookie=value", result.getCookieText());
        // 跳过自动滑块，直接走账密登录
        verify(xianyuSliderStealthService, never()).verify(1L, "cookie", captchaUrl, false);
        verify(xianyuSliderStealthService, never()).verify(1L, "cookie", captchaUrl, true);
        verify(passwordLoginService).tryPasswordLogin(1L);
    }

    private Page mockCaptchaPage(String title, String url, boolean hasCaptcha) {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);
        when(page.locator(any())).thenReturn(locator);
        when(locator.count()).thenReturn(hasCaptcha ? 1 : 0);
        when(page.title()).thenReturn(title);
        when(page.url()).thenReturn(url);
        when(page.content()).thenReturn(hasCaptcha ? "请完成验证" : "");
        return page;
    }

    private BrowserPool.BrowserInstance browserInstance(Page page, boolean headless) {
        return browserInstance(page, headless, List.of());
    }

    private BrowserPool.BrowserInstance browserInstance(Page page, boolean headless,
                                                        List<com.microsoft.playwright.options.Cookie> cookies) {
        BrowserContext context = mock(BrowserContext.class);
        when(page.context()).thenReturn(context);
        when(context.cookies()).thenReturn(cookies);
        return new BrowserPool.BrowserInstance(
                mock(Playwright.class),
                mock(Browser.class),
                context,
                page,
                headless
        );
    }

}
