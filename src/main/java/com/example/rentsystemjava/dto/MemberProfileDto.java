package com.example.rentsystemjava.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/** 当前登录成员信息（登录响应 / GET /api/auth/me）。绝不包含 SendKey 明文/密文/哈希。 */
public record MemberProfileDto(
        String username,
        String phone,
        String sendKeyTail,
        Boolean pushEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime digestSendTime) {
}
