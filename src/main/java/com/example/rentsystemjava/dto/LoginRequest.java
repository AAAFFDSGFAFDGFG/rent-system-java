package com.example.rentsystemjava.dto;

/** 登录请求（唯一无需会话的接口） */
public record LoginRequest(
        String username,
        String sendKey) {
}
