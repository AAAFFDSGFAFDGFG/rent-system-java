package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 登记出租请求（C5，空置→已租） */
public record LeaseRequest(
        String tenantName,
        String tenantPhone,
        BigDecimal actualRent,
        String paymentMode,
        BigDecimal deposit,
        Integer payDay,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal electricStart,
        BigDecimal waterStart,
        String note) {
}
