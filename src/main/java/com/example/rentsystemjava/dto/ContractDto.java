package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 生效合同（详情抽屉租赁条款区） */
public record ContractDto(
        Long id,
        BigDecimal actualRent,
        String paymentMode,
        BigDecimal deposit,
        LocalDate startDate,
        LocalDate endDate,
        Integer payDay,
        BigDecimal electricStart,
        BigDecimal waterStart,
        String tenantName,
        String tenantPhone,
        String note) {
}
