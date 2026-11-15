package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.DashboardStatsDto;
import com.example.rentsystemjava.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** B1 指标卡（四张一次取回） */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard/stats")
    public ApiResult<DashboardStatsDto> stats() {
        return ApiResult.ok(dashboardService.stats());
    }
}
