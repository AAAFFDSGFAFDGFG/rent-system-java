package com.example.rentsystemjava.domain;

import com.example.rentsystemjava.domain.converter.ChannelCodeConverter;
import com.example.rentsystemjava.domain.converter.DigestStatusConverter;
import com.example.rentsystemjava.domain.enums.ChannelCode;
import com.example.rentsystemjava.domain.enums.DigestStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 聚合日报（表 digest_report）。uk_digest(report_date, digest_type) 保证一天一份正文。
 */
@Entity
@Table(name = "digest_report")
@Getter
@Setter
@NoArgsConstructor
public class DigestReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "digest_type", nullable = false, length = 16)
    private String digestType;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vacant_snapshot")
    private String vacantSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "upcoming_snapshot")
    private String upcomingSnapshot;

    @Convert(converter = ChannelCodeConverter.class)
    @Column(nullable = false, length = 32)
    private ChannelCode channel;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Convert(converter = DigestStatusConverter.class)
    @Column(nullable = false, length = 16)
    private DigestStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
