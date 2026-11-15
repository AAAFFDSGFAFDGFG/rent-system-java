package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 续租请求（C7）。newDeposit/newPaymentMode 不传则沿用旧合同。 */
public record RenewRequest(
        LocalDate newEndDate,
        BigDecimal newActualRent,
        BigDecimal newDeposit,
        String newPaymentMode) {
}
