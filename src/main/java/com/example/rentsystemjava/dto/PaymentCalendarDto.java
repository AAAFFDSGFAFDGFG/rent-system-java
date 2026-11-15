package com.example.rentsystemjava.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 当月付款日历（后端算好日期格与高亮，前端只画） */
public record PaymentCalendarDto(
        int year,
        int month,
        int payDay,
        int today,
        List<DayCell> days) {

    /** 单个日期格：day=0 表示月初对齐用的空格 */
    public record DayCell(
            @JsonProperty("day") int day,
            @JsonProperty("isToday") boolean isToday,
            @JsonProperty("isPayDay") boolean isPayDay) {
    }
}
