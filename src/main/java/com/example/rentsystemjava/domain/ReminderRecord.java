package com.example.rentsystemjava.domain;

import com.example.rentsystemjava.domain.converter.ChannelCodeConverter;
import com.example.rentsystemjava.domain.converter.ReminderTypeConverter;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import com.example.rentsystemjava.domain.enums.ReminderType;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 已发送提醒事项流水（表 reminder_record，方案 B）。
 * 不预生成待办池；仅在日报生成时，把写进 {临期事项清单} 的事项逐条落库。
 * 日历/临期列表不查本表，仍由 lease_contract 实时计算。
 */
@Entity
@Table(name = "reminder_record")
@Getter
@Setter
@NoArgsConstructor
public class ReminderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "house_id", nullable = false)
    private Long houseId;

    @Column(name = "contract_id")
    private Long contractId;

    @Convert(converter = ReminderTypeConverter.class)
    @Column(nullable = false, length = 16)
    private ReminderType type;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "digest_id", nullable = false)
    private Long digestId;

    @Convert(converter = ChannelCodeConverter.class)
    @Column(nullable = false, length = 32)
    private ChannelCode channel;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
