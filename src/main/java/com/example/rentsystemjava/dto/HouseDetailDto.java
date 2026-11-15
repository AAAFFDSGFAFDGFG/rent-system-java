package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.util.List;

/** 房源详情（C1，已租/空置通用；contract 为 null 表示空置） */
public record HouseDetailDto(
        Long id,
        String community,
        String building,
        Integer floorNo,
        String roomNo,
        String layout,
        BigDecimal area,
        String face,
        BigDecimal expectedRent,
        BigDecimal displayRent,
        String status,
        List<String> tags,
        ContractDto contract,
        LeaseProgressDto leaseProgress,
        PaymentCalendarDto paymentCalendar,
        NotifyUserDto notifyUser,
        Integer remindAdvanceDays,
        List<ContractHistoryDto> contractHistory) {
}
