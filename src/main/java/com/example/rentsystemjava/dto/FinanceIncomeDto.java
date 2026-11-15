package com.example.rentsystemjava.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 财产卡收入（B5）。amount 为租金累计；forfeitedDeposit 独立统计不并入 amount。 */
public record FinanceIncomeDto(
        Integer year,
        Integer month,
        BigDecimal amount,
        BigDecimal monthExpected,
        BigDecimal yearTotal,
        BigDecimal forfeitedDeposit,
        List<ForfeitedDetailDto> forfeitedDetails,
        List<BreakdownDto> breakdown,
        LocalDate asOf) {

    /** 单合同分段明细（便于核对） */
    public record BreakdownDto(
            Long houseId,
            Long contractId,
            long days,
            BigDecimal dailyRent,
            BigDecimal amount) {
    }

    /** 没收押金逐笔明细（违约退租） */
    public record ForfeitedDetailDto(
            Long houseId,
            Long contractId,
            BigDecimal amount,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime terminatedAt,
            String tenantName) {
    }
}
