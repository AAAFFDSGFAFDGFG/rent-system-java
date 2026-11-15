package com.example.rentsystemjava.domain.enums;

/** 合同状态：active / terminated */
public enum ContractStatus implements ValuedEnum {
    ACTIVE("active"),
    TERMINATED("terminated");

    private final String value;

    ContractStatus(String value) { this.value = value; }

    public String getValue() { return value; }
}
