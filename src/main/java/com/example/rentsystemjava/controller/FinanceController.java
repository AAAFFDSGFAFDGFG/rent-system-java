package com.example.rentsystemjava.controller;

import com.example.rentsystemjava.common.ApiResult;
import com.example.rentsystemjava.dto.FinanceIncomeDto;
import com.example.rentsystemjava.service.FinanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** B5 财产卡：month 缺省或 all = 全年累计 */
@RestController
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/api/finance/income")
    public ApiResult<FinanceIncomeDto> income(
            @RequestParam Integer year,
            @RequestParam(required = false) String month) {
        return ApiResult.ok(financeService.income(year, month));
    }
}
