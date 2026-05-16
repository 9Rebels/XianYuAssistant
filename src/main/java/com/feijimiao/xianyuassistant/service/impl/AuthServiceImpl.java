package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.cache.CacheService;
import com.feijimiao.xianyuassistant.entity.SysLoginToken;
import com.feijimiao.xianyuassistant.entity.SysUser;
import com.feijimiao.xianyuassistant.mapper.SysLoginTokenMapper;
import com.feijimiao.xianyuassistant.mapper.SysUserMapper;
import com.feijimiao.xianyuassistant.service.AuthService;
import com.feijimiao.xianyuassistant.service.bo.*;
import com.feijimiao.xianyuassistant.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 * @author IAMLZY
 * @date 2026/4/22
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 登录错误限制：10分钟内最多5次 */
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_ATTEMPT_WINDOW_MS = 10 * 60 * 1000L;

    /** 缓存key前缀 */
    private static final String LOGIN_ATTEMPT_PREFIX = "login_attempt:";
    private static final int TOKEN_STATUS_ACTIVE = 1;
    private static final int TOKEN_STATUS_LOGGED_OUT = 0;
    private static final int TOKEN_STATUS_KICKED = -1;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysLoginTokenMapper sysLoginTokenMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CacheService cacheService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public CheckUserExistsRespBO checkUserExists() {
        CheckUserExistsRespBO respBO = new CheckUserExistsRespBO();
        long count = sysUserMapper.selectCount(null);
        respBO.setExists(count > 0);
        return respBO;
    }

    @Override
    public LoginRespBO register(RegisterReqBO reqBO) {
        // 检查是否已有用户
        long count = sysUserMapper.selectCount(null);
        if (count > 0) {
            throw new RuntimeException("已有账号，无法注册");
        }

        // 检查用户名是否重复
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, reqBO.getUsername());
        SysUser existing = sysUserMapper.selectOne(wrapper);
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(reqBO.getUsername());
        user.setPassword(passwordEncoder.encode(reqBO.getPassword()));
        user.setStatus(1);
        user.setCreatedTime(LocalDateTime.now().format(FORMATTER));
        user.setUpdatedTime(LocalDateTime.now().format(FORMATTER));
        sysUserMapper.insert(user);

        log.info("[Auth] 注册成功: username={}", reqBO.getUsername());

        // 注册后自动登录
        LoginReqBO loginReqBO = new LoginReqBO();
        loginReqBO.setUsername(reqBO.getUsername());
        loginReqBO.setPassword(reqBO.getPassword());
        loginReqBO.setIp(reqBO.getIp());
        loginReqBO.setDeviceId(reqBO.getDeviceId());
        loginReqBO.setUserAgent(reqBO.getUserAgent());
        return login(loginReqBO);
    }

    @Override
    public LoginRespBO login(LoginReqBO reqBO) {
        // 查找用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, reqBO.getUsername());
        SysUser user = sysUserMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 验证密码
        if (!passwordEncoder.matches(reqBO.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 保存新Token到数据库
        String now = LocalDateTime.now().format(FORMATTER);
        SysLoginToken loginToken = new SysLoginToken();
        loginToken.setUserId(user.getId());
        loginToken.setToken(token);
        loginToken.setDeviceId(reqBO.getDeviceId());
        loginToken.setDeviceName(buildDeviceName(reqBO.getUserAgent()));
        loginToken.setBrowserName(parseBrowserName(reqBO.getUserAgent()));
        loginToken.setOsName(parseOsName(reqBO.getUserAgent()));
        loginToken.setUserAgent(reqBO.getUserAgent());
        loginToken.setLoginIp(reqBO.getIp());
        loginToken.setExpireTime(LocalDateTime.now().plusDays(30).format(FORMATTER));
        loginToken.setLastActiveTime(now);
        loginToken.setStatus(TOKEN_STATUS_ACTIVE);
        loginToken.setCreatedTime(now);
        loginToken.setUpdatedTime(now);
        sysLoginTokenMapper.insert(loginToken);

        // 更新用户最后登录信息
        user.setLastLoginTime(now);
        user.setLastLoginIp(reqBO.getIp());
        sysUserMapper.updateById(user);

        // 缓存Token（提高性能）
        cacheService.put("token:" + token, user.getId(), 30, TimeUnit.DAYS);

        log.info("[Auth] 登录成功: username={}, ip={}", reqBO.getUsername(), reqBO.getIp());

        LoginRespBO respBO = new LoginRespBO();
        respBO.setToken(token);
        respBO.setUsername(user.getUsername());
        return respBO;
    }

    @Override
    public boolean isTokenValid(String token) {
        // 先查缓存
        Object cached = cacheService.get("token:" + token);
        if (cached != null) {
            return true;
        }

        // 缓存未命中，查数据库
        LambdaQueryWrapper<SysLoginToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginToken::getToken, token);
        SysLoginToken loginToken = sysLoginTokenMapper.selectOne(wrapper);

        if (loginToken == null) {
            return false;
        }
        if (effectiveStatus(loginToken) != TOKEN_STATUS_ACTIVE) {
            cacheService.remove("token:" + token);
            return false;
        }

        // 检查是否过期
        try {
            LocalDateTime expireTime = LocalDateTime.parse(loginToken.getExpireTime(), FORMATTER);
            if (expireTime.isBefore(LocalDateTime.now())) {
                loginToken.setStatus(TOKEN_STATUS_LOGGED_OUT);
                loginToken.setUpdatedTime(LocalDateTime.now().format(FORMATTER));
                sysLoginTokenMapper.updateById(loginToken);
                cacheService.remove("token:" + token);
                return false;
            }
        } catch (Exception e) {
            log.warn("[Auth] 解析Token过期时间失败: {}", loginToken.getExpireTime());
        }

        // 回填缓存
        cacheService.put("token:" + token, loginToken.getUserId(), 30, TimeUnit.DAYS);
        return true;
    }

    @Override
    public void touchToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<SysLoginToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginToken::getToken, token);
        SysLoginToken loginToken = sysLoginTokenMapper.selectOne(wrapper);
        if (loginToken == null || effectiveStatus(loginToken) != TOKEN_STATUS_ACTIVE) {
            return;
        }
        loginToken.setLastActiveTime(LocalDateTime.now().format(FORMATTER));
        sysLoginTokenMapper.updateById(loginToken);
    }

    @Override
    public boolean checkLoginAttempt(String ip) {
        String key = LOGIN_ATTEMPT_PREFIX + ip;
        Object count = cacheService.get(key);
        if (count == null) {
            return true;
        }
        if (count instanceof Number) {
            return ((Number) count).intValue() < MAX_LOGIN_ATTEMPTS;
        }
        // AtomicLong类型
        try {
            long val = Long.parseLong(count.toString());
            return val < MAX_LOGIN_ATTEMPTS;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void recordLoginFailure(String ip) {
        String key = LOGIN_ATTEMPT_PREFIX + ip;
        if (!cacheService.containsKey(key)) {
            // 首次失败，设置10分钟过期
            cacheService.put(key, cacheService.increment(key), LOGIN_ATTEMPT_WINDOW_MS, TimeUnit.MILLISECONDS);
        } else {
            cacheService.increment(key);
            // 确保过期时间存在
            if (cacheService.getExpire(key) == -1) {
                cacheService.expire(key, LOGIN_ATTEMPT_WINDOW_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public void clearLoginFailure(String ip) {
        String key = LOGIN_ATTEMPT_PREFIX + ip;
        cacheService.remove(key);
    }

    @Override
    public SysUser getCurrentUser(Long userId) {
        return sysUserMapper.selectById(userId);
    }

    @Override
    public void changePassword(ChangePasswordReqBO reqBO) {
        SysUser user = sysUserMapper.selectById(reqBO.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(reqBO.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(reqBO.getNewPassword()));
        user.setUpdatedTime(LocalDateTime.now().format(FORMATTER));
        sysUserMapper.updateById(user);
        log.info("[Auth] 修改密码成功: userId={}", reqBO.getUserId());
    }

    @Override
    public List<LoginDeviceBO> listLoginDevices(Long userId, String currentToken) {
        LambdaQueryWrapper<SysLoginToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginToken::getUserId, userId);
        wrapper.orderByDesc(SysLoginToken::getCreatedTime);
        List<SysLoginToken> tokens = sysLoginTokenMapper.selectList(wrapper);
        List<LoginDeviceBO> devices = new ArrayList<>();
        for (SysLoginToken token : tokens) {
            devices.add(toLoginDevice(token, currentToken));
        }
        return devices;
    }

    @Override
    public void kickLoginDevice(Long userId, Long tokenId, String currentToken) {
        SysLoginToken loginToken = sysLoginTokenMapper.selectById(tokenId);
        if (loginToken == null || !userId.equals(loginToken.getUserId())) {
            throw new RuntimeException("登录设备不存在");
        }
        if (loginToken.getToken() != null && loginToken.getToken().equals(currentToken)) {
            throw new RuntimeException("不能踢出当前设备，请使用退出登录");
        }
        loginToken.setStatus(TOKEN_STATUS_KICKED);
        loginToken.setUpdatedTime(LocalDateTime.now().format(FORMATTER));
        sysLoginTokenMapper.updateById(loginToken);
        cacheService.remove("token:" + loginToken.getToken());
        log.info("[Auth] 踢出登录设备: userId={}, tokenId={}", userId, tokenId);
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<SysLoginToken> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginToken::getToken, token);
        SysLoginToken loginToken = sysLoginTokenMapper.selectOne(wrapper);
        if (loginToken != null) {
            loginToken.setStatus(TOKEN_STATUS_LOGGED_OUT);
            loginToken.setUpdatedTime(LocalDateTime.now().format(FORMATTER));
            sysLoginTokenMapper.updateById(loginToken);
        }

        // 删除缓存中的Token
        cacheService.remove("token:" + token);

        log.info("[Auth] 退出登录成功: token={}", token);
    }

    private LoginDeviceBO toLoginDevice(SysLoginToken token, String currentToken) {
        LoginDeviceBO device = new LoginDeviceBO();
        device.setId(token.getId());
        device.setDeviceName(firstText(token.getDeviceName(), buildDeviceName(token.getUserAgent())));
        device.setBrowserName(firstText(token.getBrowserName(), parseBrowserName(token.getUserAgent())));
        device.setOsName(firstText(token.getOsName(), parseOsName(token.getUserAgent())));
        device.setLoginIp(token.getLoginIp());
        device.setLoginTime(token.getCreatedTime());
        device.setLastActiveTime(token.getLastActiveTime());
        device.setExpireTime(token.getExpireTime());
        device.setStatus(effectiveStatus(token));
        device.setCurrent(token.getToken() != null && token.getToken().equals(currentToken));
        return device;
    }

    private int effectiveStatus(SysLoginToken token) {
        return token.getStatus() == null ? TOKEN_STATUS_ACTIVE : token.getStatus();
    }

    private String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String buildDeviceName(String userAgent) {
        String os = parseOsName(userAgent);
        String browser = parseBrowserName(userAgent);
        if ("未知设备".equals(os) && "未知浏览器".equals(browser)) {
            return "未知设备";
        }
        return os + " · " + browser;
    }

    private String parseOsName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知设备";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        return "未知设备";
    }

    private String parseBrowserName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知浏览器";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("chrome/") || ua.contains("crios/")) return "Chrome";
        if (ua.contains("safari/")) return "Safari";
        return "未知浏览器";
    }
}
