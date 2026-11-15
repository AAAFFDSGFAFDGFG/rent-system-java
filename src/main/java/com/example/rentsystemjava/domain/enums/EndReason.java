package com.example.rentsystemjava.domain.enums;

/** 合同终止原因：expired 到期退租 / breach 提前违约 / renewed 续租换约；active 时为 NULL */
public enum EndReason implements ValuedEnum {
    EXPIRED("expired"),
    BREACH("breach"),
    RENEWED("renewed");

    private final String value;

    EndReason(String value) { this.value = value; }

    public String getValue() { return value; }
}
