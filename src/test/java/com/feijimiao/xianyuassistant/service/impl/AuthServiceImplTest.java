package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.cache.CacheService;
import com.feijimiao.xianyuassistant.entity.SysLoginToken;
import com.feijimiao.xianyuassistant.entity.SysUser;
import com.feijimiao.xianyuassistant.mapper.SysLoginTokenMapper;
import com.feijimiao.xianyuassistant.mapper.SysUserMapper;
import com.feijimiao.xianyuassistant.service.bo.LoginReqBO;
import com.feijimiao.xianyuassistant.service.bo.LoginRespBO;
import com.feijimiao.xianyuassistant.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    @Test
    void loginKeepsExistingSessionsForMultiDeviceLogin() {
        AuthServiceImpl service = new AuthServiceImpl();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysLoginTokenMapper tokenMapper = mock(SysLoginTokenMapper.class);
        CacheService cacheService = mock(CacheService.class);
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "xianyu-assistant-jwt-secret-key-2026-04-22-very-long-secret");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 2_592_000_000L);
        ReflectionTestUtils.setField(service, "sysUserMapper", userMapper);
        ReflectionTestUtils.setField(service, "sysLoginTokenMapper", tokenMapper);
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
        ReflectionTestUtils.setField(service, "jwtUtil", jwtUtil);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(new BCryptPasswordEncoder().encode("secret123"));
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(tokenMapper.insert(any(SysLoginToken.class))).thenReturn(1);
        when(userMapper.updateById(eq(user))).thenReturn(1);

        LoginRespBO first = service.login(loginReq("Chrome Windows"));
        LoginRespBO second = service.login(loginReq("Chrome Android"));

        assertNotNull(first.getToken());
        assertNotNull(second.getToken());
        assertNotEquals(first.getToken(), second.getToken());
        verify(tokenMapper, never()).delete(any());
        verify(tokenMapper, times(2)).insert(any(SysLoginToken.class));
    }

    private LoginReqBO loginReq(String userAgent) {
        LoginReqBO reqBO = new LoginReqBO();
        reqBO.setUsername("admin");
        reqBO.setPassword("secret123");
        reqBO.setIp("127.0.0.1");
        reqBO.setDeviceId(userAgent);
        reqBO.setUserAgent(userAgent);
        return reqBO;
    }
}
