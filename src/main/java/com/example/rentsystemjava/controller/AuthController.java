package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.domain.Member;
import com.example.rentsystemjava.dto.LoginRequest;
import com.example.rentsystemjava.dto.MemberProfileDto;
import com.example.rentsystemjava.security.SessionManager;
import com.example.rentsystemjava.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证控制层（链路 A）。
 * POST /api/auth/login 唯一无需会话；POST /api/auth/logout 使会话失效。
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final SessionManager sessionManager;

    public AuthController(AuthService authService, SessionManager sessionManager) {
        this.authService = authService;
        this.sessionManager = sessionManager;
    }

    @PostMapping("/api/auth/login")
    public ApiResult<MemberProfileDto> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        Member member = authService.login(request.username(), request.sendKey());
        String token = sessionManager.create(member.getId());
        // HttpOnly + SameSite=Lax；局域网 HTTP 不设 Secure
        ResponseCookie cookie = ResponseCookie.from(SessionManager.COOKIE_NAME, token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(24))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ApiResult.ok(authService.toProfile(member));
    }

    @PostMapping("/api/auth/logout")
    public ApiResult<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = findToken(request);
        sessionManager.remove(token);
        ResponseCookie expired = ResponseCookie.from(SessionManager.COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ApiResult.ok(null);
    }

    private static String findToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (SessionManager.COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
