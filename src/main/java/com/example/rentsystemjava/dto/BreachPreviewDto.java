package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** code=2006 违约退租预览（不落库，仅驱动前端弹窗） */
public record BreachPreviewDto(
        Long houseId,
        String houseName,
        String tenantName,
        LocalDate endDate,
        LocalDate today,
        long daysEarly,
        BigDecimal deposit,
        String message) {
}
