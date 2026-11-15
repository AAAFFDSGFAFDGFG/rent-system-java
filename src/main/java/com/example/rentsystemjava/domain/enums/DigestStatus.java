package com.example.rentsystemjava.domain.enums;

/** 聚合日报状态：generated 已生成 / dispatched 已分发 / partial 部分失败 */
public enum DigestStatus implements ValuedEnum {
    GENERATED("generated"),
    DISPATCHED("dispatched"),
    PARTIAL("partial");

    private final String value;

    DigestStatus(String value) { this.value = value; }

    public String getValue() { return value; }
}
