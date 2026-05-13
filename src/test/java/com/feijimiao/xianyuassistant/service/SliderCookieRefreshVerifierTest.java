package com.feijimiao.xianyuassistant.service;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SliderCookieRefreshVerifierTest {

    @Test
    void meaningfulRefreshAcceptsChangedX5Cookie() {
        SliderCookieRefreshVerifier verifier = verifier();

        assertTrue(verifier.hasMeaningfulCookieRefresh(
                Map.of("x5secdata", "old"),
                Map.of("x5secdata", "new")
        ));
    }

    @Test
    void meaningfulRefreshAcceptsChangedKeySessionCookie() {
        SliderCookieRefreshVerifier verifier = verifier();

        assertTrue(verifier.hasMeaningfulCookieRefresh(
                Map.of("_m_h5_tk", "old"),
                Map.of("_m_h5_tk", "new")
        ));
    }

    @Test
    void unchangedCookiesAreRejectedWhenPageStillLooksLikeVerification() {
        SliderCookieRefreshVerifier verifier = verifier();
        BrowserContext context = mock(BrowserContext.class);
        Page page = page(
                "https://h5api.m.goofish.com/h5/foo/punish?x5secdata=abc&x5step=2&action=captcha",
                "验证码拦截",
                "请完成验证",
                false
        );
        when(context.cookies(any(List.class))).thenReturn(List.of(
                cookie("unb", "u"),
                cookie("sgcookie", "sg"),
                cookie("cookie2", "c2"),
                cookie("_m_h5_tk", "tk"),
                cookie("_m_h5_tk_enc", "enc"),
                cookie("t", "t")
        ));

        SliderCookieRefreshVerifier.CookieRefreshCheck result = verifier.verify(
                context,
                page,
                Map.of(
                        "unb", "u",
                        "sgcookie", "sg",
                        "cookie2", "c2",
                        "_m_h5_tk", "tk",
                        "_m_h5_tk_enc", "enc",
                        "t", "t"
                ),
                Map.of()
        );

        assertFalse(result.isAccepted());
    }

    @Test
    void softSuccessAllowedWhenLoginCookiesCompleteAndPageLeftVerificationRoute() {
        SliderCookieRefreshVerifier verifier = verifier();
        BrowserContext context = mock(BrowserContext.class);
        Page page = page("https://www.goofish.com/im", "闲鱼", "", false);
        List<Cookie> cookies = List.of(
                cookie("unb", "u"),
                cookie("sgcookie", "sg"),
                cookie("cookie2", "c2"),
                cookie("_m_h5_tk", "tk"),
                cookie("_m_h5_tk_enc", "enc"),
                cookie("t", "t")
        );
        when(context.cookies(any(List.class))).thenReturn(cookies);
        Map<String, String> baseline = Map.of(
                "unb", "u",
                "sgcookie", "sg",
                "cookie2", "c2",
                "_m_h5_tk", "tk",
                "_m_h5_tk_enc", "enc",
                "t", "t"
        );

        SliderCookieRefreshVerifier.CookieRefreshCheck result =
                verifier.verify(context, page, baseline, Map.of());

        assertTrue(result.isAccepted());
        assertTrue(result.isSoftSuccess());
    }

    private SliderCookieRefreshVerifier verifier() {
        return new SliderCookieRefreshVerifier(new SliderPageInspector(), 0L, 1L);
    }

    private Page page(String url, String title, String content, boolean visibleSlider) {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);
        when(page.isClosed()).thenReturn(false);
        when(page.url()).thenReturn(url);
        when(page.title()).thenReturn(title);
        when(page.content()).thenReturn(content);
        when(page.locator(any())).thenReturn(locator);
        when(locator.count()).thenReturn(visibleSlider ? 1 : 0);
        return page;
    }

    private Cookie cookie(String name, String value) {
        return new Cookie(name, value).setDomain(".goofish.com").setPath("/");
    }
}
