package com.example.rentsystemjava.service;

import com.example.rentsystemjava.common.BusinessException;
import com.example.rentsystemjava.common.ErrorCode;
import com.example.rentsystemjava.domain.House;
import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.EndReason;
import com.example.rentsystemjava.domain.enums.HouseStatus;
import com.example.rentsystemjava.dto.BreachPreviewDto;
import com.example.rentsystemjava.dto.HouseDetailDto;
import com.example.rentsystemjava.dto.LeaseRequest;
import com.example.rentsystemjava.dto.RenewRequest;
import com.example.rentsystemjava.repository.LeaseContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 合同状态机服务（链路 B 登记出租 / C 两阶段违约退租 / D 续租）。
 * 所有变更单事务完成；一房一生效合同由 uk_contract_active 兜底（重复提交 → 2002/2005）。
 */
@Service
public class ContractService {

    private final HouseService houseService;
    private final LeaseContractRepository contractRepository;

    public ContractService(HouseService houseService, LeaseContractRepository contractRepository) {
        this.houseService = houseService;
        this.contractRepository = contractRepository;
    }

    // ---------------- 链路 B：登记出租（空置→已租） ----------------

    @Transactional
    public HouseDetailDto lease(Long houseId, LeaseRequest req, Long currentMemberId) {
        // 1. 房源存在（2001）
        House house = houseService.requireHouse(houseId);
        // 2. 状态必须 vacant（2002）
        if (house.getStatus() != HouseStatus.VACANT) {
            throw new BusinessException(ErrorCode.HOUSE_STATUS_CONFLICT, "房源已出租，不能重复登记");
        }
        // 3. 业务校验（顺序与错误码固定）
        validateLease(req);
        // 4. 插入生效合同
        LeaseContract contract = new LeaseContract();
        contract.setHouseId(houseId);
        contract.setActualRent(req.actualRent());
        contract.setPaymentMode(req.paymentMode() == null || req.paymentMode().isBlank() ? "押一付三" : req.paymentMode());
        contract.setDeposit(req.deposit() == null ? BigDecimal.ZERO : req.deposit());
        contract.setStartDate(req.startDate());
        contract.setEndDate(req.endDate());
        contract.setPayDay(req.payDay() == null ? (byte) 1 : req.payDay().byteValue());
        contract.setElectricStart(req.electricStart());
        contract.setWaterStart(req.waterStart());
        contract.setTenantName(req.tenantName().trim());
        contract.setTenantPhone(req.tenantPhone());
        contract.setNote(req.note());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setForfeitedDeposit(BigDecimal.ZERO);
        contractRepository.save(contract);
        // 5. 房源转已租
        house.setStatus(HouseStatus.RENTED);
        // 6. 返回更新后详情
        return houseService.detail(houseId, currentMemberId);
    }

    private void validateLease(LeaseRequest req) {
        if (req.tenantName() == null || req.tenantName().isBlank()) {
            throw new BusinessException(ErrorCode.REQUIRED_FIELD_MISSING, "租客姓名必填");
        }
        if (req.actualRent() == null || req.actualRent().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.RENT_MUST_BE_POSITIVE);
        }
        if (req.startDate() == null || req.endDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "起租日与到期日必填");
        }
        if (!req.endDate().isAfter(req.startDate())) {
            throw new BusinessException(ErrorCode.END_DATE_MUST_BE_LATER);
        }
        if (req.payDay() != null && (req.payDay() < 1 || req.payDay() > 31)) {
            throw new BusinessException(ErrorCode.PAY_DAY_OUT_OF_RANGE);
        }
        if (req.deposit() != null && req.deposit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.NUMBER_FORMAT_INVALID, "押金不能为负数");
        }
        if (req.electricStart() != null && req.electricStart().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.NUMBER_FORMAT_INVALID, "入住电表度数不能为负数");
        }
        if (req.waterStart() != null && req.waterStart().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.NUMBER_FORMAT_INVALID, "用水起始吨数不能为负数");
        }
    }

    // ---------------- 链路 C：退租（两阶段违约确认） ----------------

    @Transactional
    public HouseDetailDto vacate(Long houseId, boolean confirmBreach, Long currentMemberId) {
        // 1. 房源必须 rented（2002）
        House house = houseService.requireHouse(houseId);
        if (house.getStatus() != HouseStatus.RENTED) {
            throw new BusinessException(ErrorCode.HOUSE_STATUS_CONFLICT, "空置房源无需退租");
        }
        // 2. 必须有生效合同（2005）
        LeaseContract contract = contractRepository
                .findByHouseIdAndStatus(houseId, ContractStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_CONTRACT_NOT_FOUND));
        // 3. 服务端今日（不信任前端）
        LocalDate today = LocalDate.now();
        if (today.isBefore(contract.getEndDate())) {
            // ---- 提前违约 ----
            if (!confirmBreach) {
                // 第一次请求：不写任何数据，返回 409/2006 + 违约预览
                long daysEarly = ChronoUnit.DAYS.between(today, contract.getEndDate());
                String message = "该租客提前退租，押金 ¥" + contract.getDeposit().toPlainString()
                        + " 不予退还，确认后将计入财产明细";
                throw new BusinessException(ErrorCode.BREACH_CONFIRM_REQUIRED, ErrorCode.BREACH_CONFIRM_REQUIRED.getDefaultMessage(),
                        new BreachPreviewDto(houseId, HouseService.addressOf(house), contract.getTenantName(),
                                contract.getEndDate(), today, daysEarly, contract.getDeposit(), message));
            }
            // 已确认：没收押金
            terminate(contract, EndReason.BREACH, LocalDateTime.now(), contract.getDeposit());
        } else {
            // ---- 到期正常退租：押金退还 ----
            terminate(contract, EndReason.EXPIRED, LocalDateTime.now(), BigDecimal.ZERO);
        }
        // 4. 房源转空置
        house.setStatus(HouseStatus.VACANT);
        // 5. 返回更新后详情
        return houseService.detail(houseId, currentMemberId);
    }

    // ---------------- 链路 D：续租（旧合同归档 + 新合同生效） ----------------

    @Transactional
    public HouseDetailDto renew(Long houseId, RenewRequest req, Long currentMemberId) {
        // 1. 前置状态
        House house = houseService.requireHouse(houseId);
        if (house.getStatus() != HouseStatus.RENTED) {
            throw new BusinessException(ErrorCode.HOUSE_STATUS_CONFLICT, "仅已出租房源可续租");
        }
        LeaseContract old = contractRepository
                .findByHouseIdAndStatus(houseId, ContractStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTIVE_CONTRACT_NOT_FOUND));
        // 2. 校验
        if (req.newEndDate() == null || !req.newEndDate().isAfter(old.getEndDate())) {
            throw new BusinessException(ErrorCode.RENEW_END_DATE_INVALID);
        }
        if (req.newActualRent() == null || req.newActualRent().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.RENEW_RENT_MUST_BE_POSITIVE);
        }
        if (req.newDeposit() != null && req.newDeposit().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.NUMBER_FORMAT_INVALID, "押金不能为负数");
        }
        // 3. 旧合同终止：terminated_at = 旧 endDate + 1 天的 00:00（计租上界=旧 endDate，与新合同无缝衔接）
        LocalDate handover = old.getEndDate().plusDays(1);
        terminate(old, EndReason.RENEWED, handover.atStartOfDay(), BigDecimal.ZERO);
        // 4. 新合同：继承旧合同字段，按新价生效
        LeaseContract fresh = new LeaseContract();
        fresh.setHouseId(houseId);
        fresh.setActualRent(req.newActualRent());
        fresh.setPaymentMode(req.newPaymentMode() == null || req.newPaymentMode().isBlank()
                ? old.getPaymentMode() : req.newPaymentMode());
        fresh.setDeposit(req.newDeposit() == null ? old.getDeposit() : req.newDeposit());
        fresh.setStartDate(handover);
        fresh.setEndDate(req.newEndDate());
        fresh.setPayDay(old.getPayDay());
        fresh.setElectricStart(old.getElectricStart());
        fresh.setWaterStart(old.getWaterStart());
        fresh.setTenantName(old.getTenantName());
        fresh.setTenantPhone(old.getTenantPhone());
        fresh.setNote(appendRenewNote(old));
        fresh.setStatus(ContractStatus.ACTIVE);
        fresh.setForfeitedDeposit(BigDecimal.ZERO);
        fresh.setPredecessorId(old.getId());
        contractRepository.save(fresh);
        // 5. house.status 保持 rented；6. 返回详情
        return houseService.detail(houseId, currentMemberId);
    }

    // ---------------- 私有 ----------------

    private void terminate(LeaseContract contract, EndReason reason, LocalDateTime terminatedAt, BigDecimal forfeited) {
        contract.setStatus(ContractStatus.TERMINATED);
        contract.setEndReason(reason);
        contract.setTerminatedAt(terminatedAt);
        contract.setForfeitedDeposit(forfeited);
        // end_date 保持原值不截断（历史保留「原本约定到哪天」，计租以上界为准）
        // saveAndFlush：立即落库终止状态，保证同一事务内随后的新合同 INSERT 不与旧合同同时 active
        // （否则 Hibernate 刷新顺序为 insert 先于 update，会触发 uk_contract_active 唯一约束冲突）
        contractRepository.saveAndFlush(contract);
    }

    private static String appendRenewNote(LeaseContract old) {
        String piece = "续租（上期月租 ¥" + old.getActualRent().toPlainString() + "）";
        if (old.getNote() == null || old.getNote().isBlank()) {
            return piece;
        }
        return old.getNote() + "｜" + piece;
    }
}
