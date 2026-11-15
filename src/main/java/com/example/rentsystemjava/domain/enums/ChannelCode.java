package com.example.rentsystemjava.domain.enums;

/** 通知通道：serverchan 默认 / wecom 企业微信机器人（可选降级） / sms 仅预留 */
public enum ChannelCode implements ValuedEnum {
    SERVERCHAN("serverchan"),
    WECOM("wecom"),
    SMS("sms");

    private final String value;

    ChannelCode(String value) { this.value = value; }

    public String getValue() { return value; }
}
