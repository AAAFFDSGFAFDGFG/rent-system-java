package com.example.rentsystemjava.domain.enums;

/** 房源状态：rented / vacant（数据库存小写英文） */
public enum HouseStatus implements ValuedEnum {
    RENTED("rented"),
    VACANT("vacant");

    private final String value;

    HouseStatus(String value) { this.value = value; }

    public String getValue() { return value; }
}
