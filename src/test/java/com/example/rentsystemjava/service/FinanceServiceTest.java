package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.EndReason;
import com.example.rentsystemjava.dto.FinanceIncomeDto;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 收入口径单测（链路 E）：固定「今日=2026-11-15」。
 * 覆盖：续租分段/违约计租上界/没收押金独立/未来区间为 0/全年累计/参数校验。
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock
    private LeaseContractRepository repository;

    private FinanceService service;

    /** 与种子数据/续租样例同构的合同集（今日 2026-11-15） */
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-15T04:00:00Z"), ZoneId.of("Asia/Shanghai")); // 北京时间 12:00
        service = new FinanceService(repository, clock);
    }

    private static LeaseContract contract(long id, long houseId, String rent,
                                          String start, String end,
                                          ContractStatus status, LocalDateTime terminatedAt,
                                          EndReason reason, String forfeited) {
        LeaseContract c = new LeaseContract();
        c.setId(id);
        c.setHouseId(houseId);
        c.setActualRent(new BigDecimal(rent));
        c.setStartDate(java.time.LocalDate.parse(start));
        c.setEndDate(java.time.LocalDate.parse(end));
        c.setStatus(status);
        c.setTerminatedAt(terminatedAt);
        c.setEndReason(reason);
        c.setForfeitedDeposit(new BigDecimal(forfeited));
        c.setPayDay((byte) 1);
        c.setPaymentMode("押一付三");
        c.setDeposit(new BigDecimal("1000"));
        c.setTenantName("租客" + id);
        return c;
    }

    /** 三份合同：旧续约合同(3200, terminated renewed) / 在租合同(2800) / 违约历史(3400, 11-10 10:30) */
    private void seedContracts() {
        when(repository.findAll()).thenReturn(List.of(
                contract(1, 1, "3200", "2026-05-01", "2027-04-30",
                        ContractStatus.TERMINATED, LocalDateTime.parse("2027-05-01T00:00:00"), EndReason.RENEWED, "0"),
                contract(2, 2, "2800", "2026-08-15", "2026-11-20",
                        ContractStatus.ACTIVE, null, null, "0"),
                contract(5, 3, "3400", "2026-06-01", "2026-12-31",
                        ContractStatus.TERMINATED, LocalDateTime.parse("2026-11-10T10:30:00"), EndReason.BREACH, "3400")
        ));
    }

    @Test
    void 续租旧价按旧区间_违约按上界截断_11月分段正确() {
        seedContracts();
        FinanceIncomeDto dto = service.income(2026, "11");
        // c1: 11-01~11-15 = 15 天 × 3200/30 = 1600.00
        // c2: 15 天 × 2800/30 = 1400.00
        // c5: 上界 11-09（terminated 前一天）→ 9 天 × 3400/30 = 1020.00
        assertEquals(new BigDecimal("4020.00"), dto.amount());
        assertEquals(3, dto.breakdown().size());
        // 没收押金独立统计，不并入 amount
        assertEquals(new BigDecimal("3400.00"), dto.forfeitedDeposit());
        assertEquals(1, dto.forfeitedDetails().size());
        assertEquals("租客5", dto.forfeitedDetails().get(0).tenantName());
        // 当月预计 = 当前在租生效合同合计（仅 c2）
        assertEquals(new BigDecimal("2800.00"), dto.monthExpected());
        assertEquals("2026-11-15", dto.asOf().toString());
    }

    @Test
    void 全年区间截断到今日() {
        seedContracts();
        FinanceIncomeDto dto = service.income(2026, "all");
        // c1: 05-01~11-15 = 199 天 × 106.666667 = 21226.67（末尾统一舍入）
        // c2: 08-15~11-15 = 93 天 × 93.333333 = 8680.00
        // c5: 06-01~11-09 = 162 天 × 113.333333 = 18360.00
        assertEquals(0, new BigDecimal("48266.67").compareTo(dto.yearTotal()));
        assertEquals(new BigDecimal("3400.00"), dto.forfeitedDeposit());
    }

    @Test
    void 未来区间金额为0且breakdown为空() {
        seedContracts();
        FinanceIncomeDto dto = service.income(2027, "1");
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.amount()));
        assertTrue(dto.breakdown().isEmpty());
    }

    @Test
    void 续租后历史月份收入不改变() {
        // 续租后：c1 已终止（renewed），新合同 2027-05-01 起 4200 —— 2026-11 的收入仍是旧价 3200 分段
        seedContracts();
        FinanceIncomeDto nov = service.income(2026, "11");
        var c1 = nov.breakdown().stream().filter(b -> b.contractId() == 1L).findFirst().orElseThrow();
        assertEquals(new BigDecimal("1600.00"), c1.amount());
        assertEquals(15, c1.days());
    }

    @Test
    void 参数非法返回3001() {
        assertThrows(BusinessException.class, () -> service.income(null, "11"));
        assertThrows(BusinessException.class, () -> service.income(2026, "13"));
        assertThrows(BusinessException.class, () -> service.income(2026, "abc"));
    }
}
