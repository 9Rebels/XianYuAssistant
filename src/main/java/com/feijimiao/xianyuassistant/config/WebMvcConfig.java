package com.feijimiao.xianyuassistant.config;

import com.feijimiao.xianyuassistant.interceptor.AuthInterceptor;
import com.feijimiao.xianyuassistant.interceptor.AccountPermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC 配置
 * 支持 Vue Router 的 History 模式
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private AccountPermissionInterceptor accountPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 认证拦截器（优先级1）
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**", "/ai/**")
                .excludePathPatterns("/api/login/**")
                .order(1);

        // 账号权限拦截器（优先级2，在认证之后）
        registry.addInterceptor(accountPermissionInterceptor)
                .addPathPatterns(
                        "/api/order/**",           // 订单相关
                        "/api/captcha/**",         // 滑块验证
                        "/api/statistics/**",      // 数据统计
                        "/api/excel/**",           // Excel导入导出
                        "/api/items/**",           // 商品相关
                        "/api/account/**",         // 账号管理
                        "/api/message/**",         // 消息相关
                        "/api/websocket/**"        // WebSocket相关
                )
                .excludePathPatterns(
                        "/api/auth/**",            // 登录注册
                        "/api/login/**",           // 登录
                        "/api/qrlogin/**",         // 二维码登录
                        "/api/captcha/debug-image/**", // 验证截图前端展示
                        "/api/system/**",          // 系统信息
                        "/static/**",              // 静态资源
                        "/actuator/**",            // 健康检查
                        "/error"                   // 错误页面
                )
                .order(2);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 尝试获取请求的资源
                        Resource requestedResource = location.createRelative(resourcePath);
                        
                        // 如果资源存在且可读，直接返回（静态文件、API等）
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        
                        // 如果是 API 请求，返回 null 让 Controller 处理
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }

                        // 验证码人工页已下线，避免被 SPA fallback 回 index.html
                        if ("captcha-verify.html".equals(resourcePath)) {
                            return null;
                        }
                        
                        // 其他情况返回 index.html，让 Vue Router 处理
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
