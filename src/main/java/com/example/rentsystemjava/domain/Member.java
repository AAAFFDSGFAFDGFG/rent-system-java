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

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 家庭成员（表 member）。
 * SendKey 安全口径：只存 cipher（AES-GCM 密文）+ hash（HMAC 校验值）+ tail（尾四位）；
 * 明文绝不落库、不写日志、不返回任何响应。
 */
@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(length = 32)
    private String phone;

    @Column(name = "send_key_cipher", nullable = false, length = 512)
    private String sendKeyCipher;

    @Column(name = "send_key_hash", nullable = false, length = 64)
    private String sendKeyHash;

    @Column(name = "send_key_tail", nullable = false, length = 4)
    private String sendKeyTail;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled;

    @Column(name = "digest_send_time", nullable = false)
    private LocalTime digestSendTime;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
