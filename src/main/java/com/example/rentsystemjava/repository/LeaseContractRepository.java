package com.example.rentsystemjava.repository;

import com.example.rentsystemjava.domain.LeaseContract;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.EndReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 租赁合同数据层。合同行永不物理删除；一房一生效合同由 uk_contract_active 生成列唯一约束兜底。
 */
public interface LeaseContractRepository extends JpaRepository<LeaseContract, Long> {

    Optional<LeaseContract> findByHouseIdAndStatus(Long houseId, ContractStatus status);

    /** 房源全部合同（含 terminated 历史），按起租日升序 */
    List<LeaseContract> findByHouseIdOrderByStartDateAsc(Long houseId);

    List<LeaseContract> findAllByStatus(ContractStatus status);

    /** 违约退租合同（没收押金统计用） */
    List<LeaseContract> findByStatusAndEndReason(ContractStatus status, EndReason endReason);

    List<LeaseContract> findAll();
}
