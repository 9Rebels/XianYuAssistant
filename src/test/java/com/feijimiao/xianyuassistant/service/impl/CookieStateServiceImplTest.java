package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.feijimiao.xianyuassistant.entity.XianyuCookie;
import com.feijimiao.xianyuassistant.enums.CookieStatus;
import com.feijimiao.xianyuassistant.event.cookie.CookieExpiredNotificationEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuCookieMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CookieStateServiceImplTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                XianyuCookie.class);
    }

    @Test
    void markExpiredWithNotifySkipsWhenCurrentCookieIsHealthy() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        XianyuCookie cookie = healthyCookie();
        when(cookieMapper.selectOne(any())).thenReturn(cookie);

        assertTrue(service.updateStatus(4L, CookieStatus.EXPIRED, true));

        verify(cookieMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markExpiredWithNotifyPublishesEventOnlyOnStatusTransition() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(100L);
        cookie.setCookieStatus(CookieStatus.VALID.getCode());
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(cookieMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertTrue(service.updateStatus(5L, CookieStatus.EXPIRED, true));

        verify(eventPublisher).publishEvent(eq(new CookieExpiredNotificationEvent(5L)));
    }

    @Test
    void markInvalidUpdatesLatestCookieWithoutNotification() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(101L);
        cookie.setCookieStatus(CookieStatus.VALID.getCode());
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(cookieMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertTrue(service.markInvalid(6L, "risk"));

        verify(cookieMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markExpiredWithoutNotifyDoesNotSendNotification() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(102L);
        cookie.setCookieStatus(CookieStatus.VALID.getCode());
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(cookieMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertTrue(service.markExpired(7L, false));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markExpiredWithNotifyDoesNotPublishEventWhenUpdateFails() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(104L);
        cookie.setCookieStatus(CookieStatus.VALID.getCode());
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        when(cookieMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertFalse(service.markExpired(10L, true));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateStatusReturnsFalseWhenLatestCookieMissing() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);
        when(cookieMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.updateStatus(8L, CookieStatus.EXPIRED, true));

        verify(cookieMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateFallsBackWhenStateColumnsAreNotMigrated() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();
        XianyuCookieMapper cookieMapper = mock(XianyuCookieMapper.class);
        ReflectionTestUtils.setField(service, "cookieMapper", cookieMapper);

        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(103L);
        cookie.setCookieStatus(CookieStatus.VALID.getCode());
        when(cookieMapper.selectOne(any())).thenReturn(cookie);
        doThrow(new DataAccessException("no such column: state_reason") {
        })
                .doReturn(1)
                .when(cookieMapper).update(eq(null), any(LambdaUpdateWrapper.class));

        assertTrue(service.markValid(9L));

        verify(cookieMapper, times(2)).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void isHealthyRequiresValidStatusCookieMh5TkAndLiveWebsocketToken() {
        CookieStateServiceImpl service = new CookieStateServiceImpl();

        assertTrue(service.isHealthy(healthyCookie()));

        XianyuCookie expiredToken = healthyCookie();
        expiredToken.setTokenExpireTime(System.currentTimeMillis() - 1);
        assertFalse(service.isHealthy(expiredToken));
    }

    private XianyuCookie healthyCookie() {
        XianyuCookie cookie = new XianyuCookie();
        cookie.setId(99L);
        cookie.setCookieStatus(CookieStatus.VALID.getCode());
        cookie.setCookieText("unb=1; _m_h5_tk=abc_123");
        cookie.setMH5Tk("abc_123");
        cookie.setWebsocketToken("token");
        cookie.setTokenExpireTime(System.currentTimeMillis() + 60_000);
        return cookie;
    }
}
