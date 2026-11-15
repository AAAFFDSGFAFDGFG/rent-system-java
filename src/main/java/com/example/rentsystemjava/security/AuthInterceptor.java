package com.example.rentsystemjava.security;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.common.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * 会话鉴权拦截器：拦截 /api/**（登录接口除外）。
 * 校验 RS_SESSION Cookie → memberId 放入 request attribute；无效返回 401 + code=1001。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(SessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (SessionManager.COOKIE_NAME.equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        Long memberId = sessionManager.resolve(token);
        if (memberId == null) {
            response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResult.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getDefaultMessage(), null)));
            return false;
        }
        request.setAttribute("memberId", memberId);
        return true;
    }
}
