package com.example.rentsystemjava.domain;

import com.example.rentsystemjava.domain.converter.ContractStatusConverter;
import com.example.rentsystemjava.domain.converter.EndReasonConverter;
import com.example.rentsystemjava.domain.enums.ContractStatus;
import com.example.rentsystemjava.domain.enums.EndReason;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租赁合同（表 lease_contract），保留历史、退租只终止不删行。
 * 注意：STORED 生成列 active_house_id 不映射；terminated 合同的 end_date 保持原值不截断，
 * 计租上界以 terminated_at 前一天为准（见 FinanceService）。
 */
@Entity
@Table(name = "lease_contract")
@Getter
@Setter
@NoArgsConstructor
public class LeaseContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "house_id", nullable = false)
    private Long houseId;

    @Column(name = "actual_rent", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualRent;

    @Column(name = "payment_mode", nullable = false, length = 32)
    private String paymentMode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deposit;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "pay_day", nullable = false)
    private Byte payDay;

    @Column(name = "electric_start", precision = 12, scale = 1)
    private BigDecimal electricStart;

    @Column(name = "water_start", precision = 12, scale = 1)
    private BigDecimal waterStart;

    @Column(name = "tenant_name", nullable = false, length = 64)
    private String tenantName;

    @Column(name = "tenant_phone", length = 32)
    private String tenantPhone;

    @Column(length = 255)
    private String note;

    @Convert(converter = ContractStatusConverter.class)
    @Column(nullable = false, length = 16)
    private ContractStatus status;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Convert(converter = EndReasonConverter.class)
    @Column(name = "end_reason", length = 16)
    private EndReason endReason;

    @Column(name = "forfeited_deposit", nullable = false, precision = 10, scale = 2)
    private BigDecimal forfeitedDeposit;

    @Column(name = "predecessor_id")
    private Long predecessorId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
