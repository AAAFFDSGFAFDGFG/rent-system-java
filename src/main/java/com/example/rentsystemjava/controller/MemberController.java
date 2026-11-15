package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.MemberProfileDto;
import com.example.rentsystemjava.dto.PushSettingsRequest;
import com.example.rentsystemjava.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成员控制层：
 * GET /api/auth/me 当前登录成员（顶栏问候语/推送配置/详情负责通知）；
 * PATCH /api/members/me/push-enabled 更新本人推送开关与推送时间（仅影响本人）。
 */
@RestController
public class MemberController {

    private final AuthService authService;

    public MemberController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/auth/me")
    public ApiResult<MemberProfileDto> me(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return ApiResult.ok(authService.toProfile(authService.requireMember(memberId)));
    }

    @PatchMapping("/api/members/me/push-enabled")
    public ApiResult<MemberProfileDto> updatePushSettings(@RequestBody PushSettingsRequest body,
                                                          HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return ApiResult.ok(authService.updatePushSettings(memberId, body));
    }
}
