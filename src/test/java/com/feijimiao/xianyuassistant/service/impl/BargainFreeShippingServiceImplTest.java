package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import com.feijimiao.xianyuassistant.service.BargainFreeShippingService;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BargainFreeShippingServiceImplTest {

    @Test
    void buildsPythonCompatibleDataJson() {
        BargainFreeShippingServiceImpl service = new BargainFreeShippingServiceImpl();

        assertEquals("{\"bizOrderId\":\"4502258607179022847\", \"itemId\":1048981205041,\"buyerId\":2219250854984}",
                service.buildDataJson(request()));
    }

    @Test
    void successResponseReturnsSuccessAndUpdatesCookie() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Set-Cookie", "_m_h5_tk=newtoken_1778170000000; Path=/")
                    .setBody("{\"ret\":[\"SUCCESS::调用成功\"]}"));
            server.start();

            XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
            XianyuCookie cookie = cookie("oldtoken_1778160000000");
            when(cookieMapper.selectByAccountId(1L)).thenReturn(cookie);
            BargainFreeShippingServiceImpl service = service(cookieMapper, server.url("/").toString());

            BargainFreeShippingService.FreeShippingResult result =
                    service.freeShipping(request());

            assertTrue(result.success());
            verify(cookieMapper).updateById(any(XianyuCookie.class));
            assertTrue(cookie.getCookieText().contains("_m_h5_tk=newtoken_1778170000000"));
            assertEquals("newtoken_1778170000000", cookie.getMH5Tk());
            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().contains("api=mtop.idle.groupon.activity.seller.freeshipping"));
            assertTrue(request.getPath().contains("sign="));
            String formBody = request.getBody().readUtf8();
            assertTrue(formBody.startsWith("data="));
            assertEquals(service.buildDataJson(request()),
                    URLDecoder.decode(formBody.substring("data=".length()), StandardCharsets.UTF_8));
        }
    }

    @Test
    void failResponseRetriesAndReturnsFail() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            for (int i = 0; i < 4; i++) {
                server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ret\":[\"FAIL::失败\"]}"));
            }
            server.start();

            XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
            when(cookieMapper.selectByAccountId(1L)).thenReturn(cookie("oldtoken_1778160000000"));
            BargainFreeShippingServiceImpl service = service(cookieMapper, server.url("/").toString());

            BargainFreeShippingService.FreeShippingResult result =
                    service.freeShipping(request());

            assertFalse(result.success());
            assertEquals("FAIL::失败", result.message());
            assertEquals(4, server.getRequestCount());
        }
    }

    private BargainFreeShippingServiceImpl service(XianyuCookieMapper cookieMapper, String apiUrl) {
        BargainFreeShippingServiceImpl service = new BargainFreeShippingServiceImpl();
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "apiUrl", apiUrl);
        ReflectionTestUtils.setField(service, "okHttpClient", new OkHttpClient());
        return service;
    }

    private XianyuCookie cookie(String mh5tk) {
        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(1L);
        cookie.setXianyuAccountId(1L);
        cookie.setCookieText("_m_h5_tk=" + mh5tk + "; unb=2219250854984");
        cookie.setMH5Tk(mh5tk);
        cookie.setCookieStatus(1);
        return cookie;
    }

    private BargainFreeShippingService.FreeShippingRequest request() {
        return new BargainFreeShippingService.FreeShippingRequest(
                1L, "4502258607179022847", "1048981205041", "2219250854984");
    }

}
