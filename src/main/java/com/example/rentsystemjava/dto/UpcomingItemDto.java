package com.example.rentsystemjava.dto;

import java.time.LocalDate;

/** 未来 7 天临期事项（F3 / 日报渲染 / reminder_record 落库共用） */
public record UpcomingItemDto(
        Long houseId,
        Long contractId,
        String houseName,
        String type,
        LocalDate date,
        long daysLeft,
        String tenantName,
        boolean urgent) {
}
