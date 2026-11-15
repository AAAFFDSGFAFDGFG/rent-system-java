package com.example.rentsystemjava.dto;

import java.util.List;

/** 月历热力（F1）：有事件的日期 → 红色填充；today 描边 */
public record CalendarDto(
        Integer year,
        Integer month,
        String today,
        List<EventDayDto> eventDays) {

    public record EventDayDto(
            Integer day,
            List<String> types,
            List<Long> houseIds) {
    }
}
