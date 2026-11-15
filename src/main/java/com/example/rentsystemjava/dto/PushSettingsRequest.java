package com.example.rentsystemjava.dto;

/** 更新本人推送设置（PATCH /api/members/me/push-enabled）：开关与推送时间均可选，只更新提交的字段 */
public record PushSettingsRequest(
        Boolean pushEnabled,
        String digestSendTime) {
}
