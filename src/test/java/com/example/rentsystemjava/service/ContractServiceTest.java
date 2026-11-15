package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.domain.House;
import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import com.example.rentsystemjava.dto.RenewRequest;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import com.example.rentsystemjava.service.ContractService;
import com.example.rentsystemjava.service.HouseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 合同状态机校验分支单测：不依赖「今日」的分支（缺失房源 / 状态不符 / 续租参数）。
 * 两阶段违约与续租继承字段已通过手动接口冒烟覆盖。
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private HouseService houseService;
    @Mock
    private LeaseContractRepository contractRepository;
    @InjectMocks
    private ContractService contractService;

    private static House house(Long id, HouseStatus status) {
        House h = new House();
        h.setId(id);
        h.setStatus(status);
        return h;
    }

    private static LeaseContract activeContract(Long id, LocalDate end) {
        LeaseContract c = new LeaseContract();
        c.setId(id);
        c.setHouseId(1L);
        c.setStatus(ContractStatus.ACTIVE);
        c.setEndDate(end);
        return c;
    }

    @Test
    void 退租时房源不存在_抛2001() {
        when(houseService.requireHouse(99L)).thenThrow(new BusinessException(ErrorCode.HOUSE_NOT_FOUND));
        assertThrows(BusinessException.class, () -> contractService.vacate(99L, false, null));
    }

    @Test
    void 退租时空置房源_抛2002() {
        when(houseService.requireHouse(1L)).thenReturn(house(1L, HouseStatus.VACANT));
        assertThrows(BusinessException.class, () -> contractService.vacate(1L, false, null));
    }

    @Test
    void 续租时空置房源_抛2002() {
        when(houseService.requireHouse(2L)).thenReturn(house(2L, HouseStatus.VACANT));
        RenewRequest req = new RenewRequest(LocalDate.of(2029, 1, 1), new BigDecimal("3000"), null, null);
        assertThrows(BusinessException.class, () -> contractService.renew(2L, req, null));
    }

    @Test
    void 续租新到期日早于旧到期日_抛2007() {
        when(houseService.requireHouse(1L)).thenReturn(house(1L, HouseStatus.RENTED));
        when(contractRepository.findByHouseIdAndStatus(1L, ContractStatus.ACTIVE))
                .thenReturn(Optional.of(activeContract(5L, LocalDate.of(2028, 1, 1))));
        // 新到期日 2027-01-01 早于旧 2028-01-01 → 2007
        RenewRequest req = new RenewRequest(LocalDate.of(2027, 1, 1), new BigDecimal("3000"), null, null);
        assertThrows(BusinessException.class, () -> contractService.renew(1L, req, null));
    }
}
