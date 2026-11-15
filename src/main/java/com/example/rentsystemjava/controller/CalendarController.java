package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.CalendarDto;
import com.example.rentsystemjava.dto.CalendarDayDto;
import com.example.rentsystemjava.dto.UpcomingItemDto;
import com.example.rentsystemjava.service.CalendarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F1/F2/F3 提醒日历（实时计算，不查 reminder_record） */
@RestController
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/api/calendar")
    public ApiResult<CalendarDto> calendar(@RequestParam Integer year, @RequestParam Integer month) {
        return ApiResult.ok(calendarService.calendar(year, month));
    }

    @GetMapping("/api/calendar/days/{date}")
    public ApiResult<CalendarDayDto> day(@PathVariable String date) {
        return ApiResult.ok(calendarService.dayItems(date));
    }

    @GetMapping("/api/calendar/upcoming")
    public ApiResult<List<UpcomingItemDto>> upcoming() {
        return ApiResult.ok(calendarService.upcoming());
    }
}
