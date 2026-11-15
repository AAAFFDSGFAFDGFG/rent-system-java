package com.example.rentsystemjava.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话管理（单机家庭规模，重启失效需重登，可接受）。
 * Cookie 名 RS_SESSION；TTL 24 小时（与 Max-Age 一致），惰性过期清理。
 */
@Component
public class SessionManager {

    public static final String COOKIE_NAME = "RS_SESSION";
    private static final Duration TTL = Duration.ofHours(24);

    private record Entry(Long memberId, Instant createdAt) {}

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public String create(Long memberId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Entry(memberId, Instant.now()));
        return token;
    }

    /** 返回 memberId；无效或过期返回 null */
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Entry entry = sessions.get(token);
        if (entry == null) {
            return null;
        }
        if (Instant.now().minus(TTL).isAfter(entry.createdAt())) {
            sessions.remove(token);
            return null;
        }
        return entry.memberId();
    }

    public void remove(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
