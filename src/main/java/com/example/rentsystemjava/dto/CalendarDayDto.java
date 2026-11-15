package com.example.rentsystemjava.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 点日期弹窗（F2）：当日事项清单；无事项 items=[] 前端显示空态 */
public record CalendarDayDto(
        LocalDate date,
        List<ItemDto> items) {

    public record ItemDto(
            Long houseId,
            String houseName,
            String type,
            String tenantName,
            BigDecimal actualRent,
            String paymentMode) {
    }
}
