package com.example.rentsystemjava.domain;

import com.example.rentsystemjava.domain.converter.ChannelCodeConverter;
import com.example.rentsystemjava.domain.converter.NotificationStatusConverter;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import com.example.rentsystemjava.domain.enums.NotificationStatus;
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
 * 按成员分发记录（表 notification_record）。
 * uk_notification_idem(idempotency_key) 幂等键 = 日期 + 成员 + 汇总类型；同键已成功不重发。
 */
@Entity
@Table(name = "notification_record")
@Getter
@Setter
@NoArgsConstructor
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "digest_id", nullable = false)
    private Long digestId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "digest_type", nullable = false, length = 16)
    private String digestType;

    @Convert(converter = ChannelCodeConverter.class)
    @Column(nullable = false, length = 32)
    private ChannelCode channel;

    @Convert(converter = NotificationStatusConverter.class)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
