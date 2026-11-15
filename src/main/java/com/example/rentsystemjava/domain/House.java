package com.example.rentsystemjava.domain;

import com.example.rentsystemjava.domain.converter.HouseStatusConverter;
import com.example.rentsystemjava.domain.enums.HouseStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 房源（表 house）。
 * 注意：STORED 生成列 active_location_flag 不映射，由数据库维护；
 * created_at/updated_at 由数据库维护，实体只读。
 */
@Entity
@Table(name = "house")
@Getter
@Setter
@NoArgsConstructor
public class House {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String community;

    @Column(nullable = false, length = 32)
    private String building;

    @Column(name = "floor_no", nullable = false)
    private Short floorNo;

    @Column(name = "room_no", nullable = false, length = 8)
    private String roomNo;

    @Column(nullable = false, length = 32)
    private String layout;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal area;

    @Column(nullable = false, length = 32)
    private String face;

    @Column(name = "expected_rent", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedRent;

    @Convert(converter = HouseStatusConverter.class)
    @Column(nullable = false, length = 16)
    private HouseStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags_json")
    private String tagsJson;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
