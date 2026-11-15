package com.example.rentsystemjava.service;

import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import com.example.rentsystemjava.dto.DashboardStatsDto;
import com.example.rentsystemjava.repository.HouseRepository;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 首页指标卡（B1）。
 * pendingCollectionCount「本月待收租」（套数，门 1 决策 9）：未删除、rented、有 active 合同、
 * 且本月付款日尚未到达（本月付款日 ≥ 今天；pay_day 大于当月天数取当月最后一日）。
 * paymentReminderCount「付款提醒 N 项」：未来 7 天窗口内 type='pay' 事项数。
 */
@Service
public class DashboardService {

    private final HouseRepository houseRepository;
    private final LeaseContractRepository contractRepository;
    private final CalendarService calendarService;

    public DashboardService(HouseRepository houseRepository, LeaseContractRepository contractRepository,
                            CalendarService calendarService) {
        this.houseRepository = houseRepository;
        this.contractRepository = contractRepository;
        this.calendarService = calendarService;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto stats() {
        LocalDate today = LocalDate.now();
        long total = houseRepository.countByDeletedAtIsNull();
        long rented = houseRepository.countByDeletedAtIsNullAndStatus(HouseStatus.RENTED);
        long vacant = houseRepository.countByDeletedAtIsNullAndStatus(HouseStatus.VACANT);
        long communities = houseRepository.findDistinctCommunities().size();

        // 本月待收租：本月付款日尚未到达的在租房源套数
        long pendingCollection = contractRepository.findAllByStatus(
                        com.example.rentsystemjava.domain.enums.ContractStatus.ACTIVE).stream()
                .filter(c -> {
                    int effectiveDay = Math.min(c.getPayDay(), today.lengthOfMonth());
                    return effectiveDay >= today.getDayOfMonth();
                })
                .count();

        // 付款提醒项：窗口内 pay 事项数
        long paymentReminders = calendarService.upcoming().stream()
                .filter(i -> "pay".equals(i.type()))
                .count();

        return new DashboardStatsDto(total, rented, vacant, pendingCollection, paymentReminders, communities);
    }
}
