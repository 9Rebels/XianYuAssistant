package com.feijimiao.xianyuassistant.interceptor;

import com.feijimiao.xianyuassistant.entity.SysUser;
import com.feijimiao.xianyuassistant.mapper.SysUserMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 账号权限拦截器
 *
 * 功能：
 * - 验证用户是否有权限访问指定的闲鱼账号
 * - 防止用户访问其他用户的账号数据
 * - 支持多种参数传递方式（Query、Body、Path）
 */
@Slf4j
@Component
public class AccountPermissionInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private XianyuAccountMapper xianyuAccountMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        log.debug("账号权限拦截: URI={}, Method={}", uri, method);

        // 跳过不需要验证的接口
        if (shouldSkip(uri)) {
            return true;
        }

        // 获取Token
        String token = extractToken(request);
        if (token == null || token.isEmpty()) {
            log.warn("未提供Token: URI={}", uri);
            sendError(response, 401, "未登录或Token已过期");
            return false;
        }

        // 验证Token
        Long userId;
        try {
            userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                log.warn("Token无效: URI={}", uri);
                sendError(response, 401, "Token无效");
                return false;
            }
        } catch (Exception e) {
            log.error("Token解析失败: URI={}", uri, e);
            sendError(response, 401, "Token解析失败");
            return false;
        }

        // 验证用户是否存在
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            log.warn("用户不存在或已禁用: userId={}", userId);
            sendError(response, 403, "用户不存在或已禁用");
            return false;
        }

        // 获取要访问的账号ID
        Long xianyuAccountId = extractAccountId(request);
        if (xianyuAccountId == null) {
            // 如果没有指定账号ID，允许通过（查询所有账号的情况）
            log.debug("未指定账号ID，允许通过: userId={}", userId);
            return true;
        }

        // 验证账号归属权限
        if (!hasPermission(userId, xianyuAccountId)) {
            log.warn("无权限访问账号: userId={}, xianyuAccountId={}", userId, xianyuAccountId);
            sendError(response, 403, "无权限访问该账号");
            return false;
        }

        log.debug("权限验证通过: userId={}, xianyuAccountId={}", userId, xianyuAccountId);
        return true;
    }

    /**
     * 判断是否跳过权限验证
     */
    private boolean shouldSkip(String uri) {
        // 跳过登录、注册等公开接口
        if (uri.startsWith("/api/auth/")) {
            return true;
        }
        // 跳过静态资源
        if (uri.startsWith("/static/")) {
            return true;
        }
        // 跳过健康检查
        if (uri.equals("/actuator/health") || uri.equals("/health")) {
            return true;
        }
        return false;
    }

    /**
     * 提取Token
     */
    private String extractToken(HttpServletRequest request) {
        // 从Header中获取
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // 从Cookie中获取
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 从Query参数中获取（不推荐，但支持）
        token = request.getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        return null;
    }

    /**
     * 提取账号ID
     */
    private Long extractAccountId(HttpServletRequest request) {
        // 从Query参数中获取
        String accountIdStr = request.getParameter("xianyuAccountId");
        if (accountIdStr != null && !accountIdStr.isEmpty()) {
            try {
                return Long.parseLong(accountIdStr);
            } catch (NumberFormatException e) {
                log.warn("账号ID格式错误: {}", accountIdStr);
            }
        }

        // 从Path参数中获取（如 /api/account/{id}）
        String uri = request.getRequestURI();
        if (uri.matches(".*/account/\\d+.*")) {
            String[] parts = uri.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("account".equals(parts[i]) && i + 1 < parts.length) {
                    try {
                        return Long.parseLong(parts[i + 1]);
                    } catch (NumberFormatException e) {
                        log.warn("Path中账号ID格式错误: {}", parts[i + 1]);
                    }
                }
            }
        }

        // 注意：从Body中提取需要在Controller层处理，这里无法获取
        // 因为Request的InputStream只能读取一次

        return null;
    }

    /**
     * 验证用户是否有权限访问指定账号
     *
     * 规则：
     * 1. 管理员可以访问所有账号
     * 2. 普通用户只能访问自己创建的账号
     */
    private boolean hasPermission(Long userId, Long xianyuAccountId) {
        try {
            // 查询账号信息
            var account = xianyuAccountMapper.selectById(xianyuAccountId);
            if (account == null) {
                log.warn("账号不存在: xianyuAccountId={}", xianyuAccountId);
                return false;
            }

            // TODO: 这里需要根据实际的账号归属逻辑判断
            // 如果xianyu_account表有user_id字段，可以直接比较
            // 如果没有，可以通过其他方式判断（如所有账号都属于同一用户）

            // 临时方案：允许所有已登录用户访问所有账号
            // 生产环境需要根据实际业务逻辑修改
            log.debug("权限验证通过（临时方案）: userId={}, xianyuAccountId={}", userId, xianyuAccountId);
            return true;

            // 严格方案示例（需要xianyu_account表有user_id字段）：
            // return account.getUserId().equals(userId);

        } catch (Exception e) {
            log.error("权限验证失败", e);
            return false;
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null}", status, message);
        response.getWriter().write(json);
    }
}
