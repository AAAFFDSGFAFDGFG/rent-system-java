package com.example.rentsystemjava.dto;

import com.example.rentsystemjava.domain.enums.NotificationStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 通知记录（F4 列表 / F5 补发预览） */
public record NotificationRecordDto(
        Long id,
        LocalDate reportDate,
        String channel,
        NotificationStatus status,
        String memberUsername,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime sentAt,
        Integer retryCount,
        boolean canResend) {
}
