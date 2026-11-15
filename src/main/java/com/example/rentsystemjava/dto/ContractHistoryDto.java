package com.example.rentsystemjava.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 已终止历史合同（租赁合同历史区） */
public record ContractHistoryDto(
        Long id,
        String tenantName,
        BigDecimal actualRent,
        LocalDate startDate,
        LocalDate endDate,
        String endReason,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime terminatedAt,
        BigDecimal forfeitedDeposit) {
}
