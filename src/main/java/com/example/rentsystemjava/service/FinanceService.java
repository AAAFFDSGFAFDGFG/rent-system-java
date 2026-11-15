package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.EndReason;
import com.example.rentsystemjava.dto.FinanceIncomeDto;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 财产收入计算（链路 E，口径必须与 PRD B5 / flow-diagrams §9 完全一致）：
 * - 遍历全部合同（含 terminated 历史），每份按自己的 actual_rent 分段计算
 * - 有效天数 = [max(start, 区间起), min(计租上界, 区间止, 今日)] 闭区间 +1 天
 * - 计租上界：未终止 = end_date；已终止 = min(end_date, terminated_at 前一天)
 * - dailyRent = actual_rent / 30（BigDecimal 高精度中间值，输出统一 2 位 HALF_UP）
 * - 没收押金独立统计（end_reason='breach' 且 terminated_at 落所选区间且 ≤ 今日），不并入 amount
 */
@Service
public class FinanceService {

    private static final BigDecimal THIRTY = new BigDecimal("30");

    private final LeaseContractRepository contractRepository;
    private final Clock clock;

    public FinanceService(LeaseContractRepository contractRepository, Clock clock) {
        this.contractRepository = contractRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinanceIncomeDto income(Integer year, String monthParam) {
        if (year == null || year < 2000 || year > 2200) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "year 必填且须为合法年份");
        }
        Integer month = parseMonth(monthParam);

        LocalDate today = LocalDate.now(clock);
        boolean allYear = month == null;
        LocalDate rangeStart = allYear ? LocalDate.of(year, 1, 1) : LocalDate.of(year, month, 1);
        LocalDate rangeEnd = allYear
                ? LocalDate.of(year, 12, 31)
                : rangeStart.withDayOfMonth(rangeStart.lengthOfMonth());

        List<LeaseContract> contracts = contractRepository.findAll();
        LocalDate effectiveEnd = rangeEnd.isAfter(today) ? today : rangeEnd;

        BigDecimal amount = BigDecimal.ZERO;
        List<FinanceIncomeDto.BreakdownDto> breakdown = new ArrayList<>();
        for (LeaseContract c : contracts) {
            LocalDate chargeEnd = chargeUpperBound(c);
            LocalDate s = c.getStartDate().isAfter(rangeStart) ? c.getStartDate() : rangeStart;
            LocalDate e = chargeEnd.isBefore(effectiveEnd) ? chargeEnd : effectiveEnd;
            if (s.isAfter(e)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(s, e) + 1;
            BigDecimal dailyRent = c.getActualRent().divide(THIRTY, 6, RoundingMode.HALF_UP);
            BigDecimal itemAmount = dailyRent.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
            amount = amount.add(itemAmount);
            breakdown.add(new FinanceIncomeDto.BreakdownDto(c.getHouseId(), c.getId(), days,
                    dailyRent.setScale(2, RoundingMode.HALF_UP), itemAmount));
        }

        BigDecimal forfeitedDeposit = BigDecimal.ZERO;
        List<FinanceIncomeDto.ForfeitedDetailDto> forfeitedDetails = new ArrayList<>();
        for (LeaseContract c : contracts) {
            if (c.getEndReason() != EndReason.BREACH || c.getTerminatedAt() == null) {
                continue;
            }
            LocalDate terminatedDate = c.getTerminatedAt().toLocalDate();
            if (!terminatedDate.isBefore(rangeStart) && !terminatedDate.isAfter(today)
                    && !terminatedDate.isAfter(rangeEnd)) {
                forfeitedDeposit = forfeitedDeposit.add(c.getForfeitedDeposit());
                forfeitedDetails.add(new FinanceIncomeDto.ForfeitedDetailDto(c.getHouseId(), c.getId(),
                        c.getForfeitedDeposit(), c.getTerminatedAt(), c.getTenantName()));
            }
        }

        BigDecimal monthExpected = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .map(LeaseContract::getActualRent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int currentYear = today.getYear();
        BigDecimal yearTotal = computeRangeAmount(contracts,
                LocalDate.of(currentYear, 1, 1), LocalDate.of(currentYear, 12, 31), today);

        return new FinanceIncomeDto(year, month, amount.setScale(2, RoundingMode.HALF_UP),
                monthExpected.setScale(2, RoundingMode.HALF_UP), yearTotal,
                forfeitedDeposit.setScale(2, RoundingMode.HALF_UP), forfeitedDetails, breakdown, today);
    }

    /** 区间金额计算（yearTotal 复用） */
    private BigDecimal computeRangeAmount(List<LeaseContract> contracts, LocalDate rangeStart, LocalDate rangeEnd, LocalDate today) {
        LocalDate effectiveEnd = rangeEnd.isAfter(today) ? today : rangeEnd;
        BigDecimal amount = BigDecimal.ZERO;
        for (LeaseContract c : contracts) {
            LocalDate chargeEnd = chargeUpperBound(c);
            LocalDate s = c.getStartDate().isAfter(rangeStart) ? c.getStartDate() : rangeStart;
            LocalDate e = chargeEnd.isBefore(effectiveEnd) ? chargeEnd : effectiveEnd;
            if (s.isAfter(e)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(s, e) + 1;
            amount = amount.add(c.getActualRent().divide(THIRTY, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(days)));
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 合同计租上界：未终止 = end_date；已终止 = min(end_date, terminated_at 前一天) */
    public static LocalDate chargeUpperBound(LeaseContract c) {
        if (c.getStatus() == ContractStatus.ACTIVE || c.getTerminatedAt() == null) {
            return c.getEndDate();
        }
        LocalDate terminatedBefore = c.getTerminatedAt().toLocalDate().minusDays(1);
        return terminatedBefore.isBefore(c.getEndDate()) ? terminatedBefore : c.getEndDate();
    }

    private Integer parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank() || "all".equalsIgnoreCase(monthParam)) {
            return null;
        }
        try {
            int m = Integer.parseInt(monthParam);
            if (m < 1 || m > 12) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "month 须为 1-12 或 all");
            }
            return m;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "month 须为 1-12 或 all");
        }
    }
}
