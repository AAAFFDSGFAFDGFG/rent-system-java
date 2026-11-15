package com.example.rentsystemjava.config;

import com.example.rentsystemjava.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册鉴权拦截器（拦截 /api/**，放行登录接口）。
 * 日期格式不在全局定制：LocalDate 默认 ISO 即 yyyy-MM-dd；
 * LocalDateTime 需要 "yyyy-MM-dd HH:mm:ss"（带空格）的字段，在 DTO 上用 @JsonFormat 标注。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }
}
