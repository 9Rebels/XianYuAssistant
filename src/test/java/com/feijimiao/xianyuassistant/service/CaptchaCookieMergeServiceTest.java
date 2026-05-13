package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaCookieMergeServiceTest {

    @Test
    void mergePreservesProtectedFieldsWhenBrowserSnapshotIsIncomplete() {
        CaptchaCookieMergeService service = new CaptchaCookieMergeService();
        Map<String, String> browserCookies = new LinkedHashMap<>();
        browserCookies.put("unb", "user-1");
        browserCookies.put("sgcookie", "sg-new");
        browserCookies.put("cookie2", "cookie2-new");
        browserCookies.put("t", "t-new");
        browserCookies.put("x5secdata", "x5-new");

        Map<String, String> responseCookies = Map.of(
                "_m_h5_tk", "tk-new",
                "_m_h5_tk_enc", "tkenc-new"
        );

        CaptchaCookieMergeService.CookieMergeResult result = service.merge(
                "unb=user-1; sgcookie=sg-old; cookie2=cookie2-old; _m_h5_tk=tk-old; "
                        + "_m_h5_tk_enc=tkenc-old; t=t-old; cna=cna-old; havana_lgc2_77=havana-old",
                browserCookies,
                responseCookies
        );

        assertEquals("cna-old", result.getMergedCookies().get("cna"));
        assertEquals("havana-old", result.getMergedCookies().get("havana_lgc2_77"));
        assertEquals("x5-new", result.getMergedCookies().get("x5secdata"));
        assertTrue(result.getPreservedProtectedFields().contains("cna"));
        assertTrue(result.getPreservedProtectedFields().contains("havana_lgc2_77"));
        assertTrue(result.getMissingRequiredFields().isEmpty());
    }

    @Test
    void mergeTreatsDifferentUnbAsAccountSwitch() {
        CaptchaCookieMergeService service = new CaptchaCookieMergeService();

        CaptchaCookieMergeService.CookieMergeResult result = service.merge(
                "unb=user-1; sgcookie=sg-old; cookie2=cookie2-old; _m_h5_tk=tk-old; "
                        + "_m_h5_tk_enc=tkenc-old; t=t-old; cna=cna-old",
                Map.of(
                        "unb", "user-2",
                        "sgcookie", "sg-new",
                        "cookie2", "cookie2-new",
                        "_m_h5_tk", "tk-new",
                        "_m_h5_tk_enc", "tkenc-new",
                        "t", "t-new"
                ),
                Map.of()
        );

        assertTrue(result.isAccountSwitched());
        assertFalse(result.getMergedCookies().containsKey("cna"));
        assertEquals(List.of("cna", "havana_lgc2_77", "_tb_token_"),
                result.getMissingProtectedFields());
    }

    @Test
    void extractSetCookieUpdatesKeepsLatestNonEmptyValues() {
        CaptchaCookieMergeService service = new CaptchaCookieMergeService();

        Map<String, String> updates = service.extractSetCookieUpdates(List.of(
                "x5secdata=first-value; Path=/; Domain=.goofish.com",
                "cookie2=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
                "_m_h5_tk=tk-from-header; Path=/",
                "x5secdata=second-value; Path=/; HttpOnly"
        ));

        assertEquals("second-value", updates.get("x5secdata"));
        assertEquals("tk-from-header", updates.get("_m_h5_tk"));
        assertFalse(updates.containsKey("cookie2"));
    }
}
