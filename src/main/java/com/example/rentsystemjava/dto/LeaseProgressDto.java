package com.example.rentsystemjava.dto;

import java.time.LocalDate;

/** 租期进度（时间轴三节点：起租-今日-到期） */
public record LeaseProgressDto(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate today,
        int percent,
        long daysToExpire) {
}
