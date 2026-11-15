package com.example.rentsystemjava.domain.enums;

/** 提醒类型：pay 付款 / expire 到期 */
public enum ReminderType implements ValuedEnum {
    PAY("pay"),
    EXPIRE("expire");

    private final String value;

    ReminderType(String value) { this.value = value; }

    public String getValue() { return value; }
}
