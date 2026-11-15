package com.example.rentsystemjava.domain;

import jakarta.persistence.Column;
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

import java.time.LocalDateTime;

/**
 * 每日分发文案模板（表 digest_template），单行配置（name=default）。
 * content_text 含 5 个变量：{日期}{空置数量}{空置房源清单}{临期数量}{临期事项清单}，无人物信息。
 */
@Entity
@Table(name = "digest_template")
@Getter
@Setter
@NoArgsConstructor
public class DigestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_json")
    private String variablesJson;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
