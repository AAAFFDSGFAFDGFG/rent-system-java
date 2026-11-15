package com.example.rentsystemjava.domain.enums;

/** 发送状态：pending / success / degraded / failed */
public enum NotificationStatus implements ValuedEnum {
    PENDING("pending"),
    SUCCESS("success"),
    DEGRADED("degraded"),
    FAILED("failed");

    private final String value;

    NotificationStatus(String value) { this.value = value; }

    public String getValue() { return value; }
}
