package com.example.rentsystemjava.common;

/**
 * 业务错误码表（与 api-design.md §1.4 一一对应）。
 * httpStatus 表达错误类别，code 表达具体业务原因。
 */
public enum ErrorCode {
    OK(0, 200, "ok"),
    UNAUTHORIZED(1001, 401, "未登录或会话已失效"),
    LOGIN_FAILED(1002, 401, "用户名或 SendKey 不正确"),
    LOGIN_LOCKED(1003, 429, "登录失败次数过多"),
    MEMBER_DISABLED(1004, 403, "成员已禁用"),
    HOUSE_NOT_FOUND(2001, 404, "房源不存在"),
    HOUSE_STATUS_CONFLICT(2002, 409, "房源状态冲突"),
    HOUSE_HAS_ACTIVE_CONTRACT(2003, 409, "存在生效合同，请先退租后再删除"),
    HOUSE_LOCATION_DUPLICATE(2004, 409, "房源位置重复"),
    ACTIVE_CONTRACT_NOT_FOUND(2005, 404, "生效合同不存在"),
    BREACH_CONFIRM_REQUIRED(2006, 409, "提前退租需要二次确认"),
    RENEW_END_DATE_INVALID(2007, 409, "续租的新到期日必须晚于原到期日"),
    VALIDATION_FAILED(3001, 422, "参数校验失败"),
    REQUIRED_FIELD_MISSING(3002, 422, "必填字段缺失"),
    RENT_MUST_BE_POSITIVE(3003, 422, "实际月租必须大于 0"),
    END_DATE_MUST_BE_LATER(3004, 422, "到期日必须晚于起租日"),
    PAY_DAY_OUT_OF_RANGE(3005, 422, "付款日须在 1-31"),
    NUMBER_FORMAT_INVALID(3006, 422, "金额/读数格式非法"),
    RENEW_RENT_MUST_BE_POSITIVE(3007, 422, "续租新月租须大于 0"),
    TEMPLATE_NOT_FOUND(4001, 404, "模板不存在"),
    TEMPLATE_EMPTY(4002, 422, "模板正文为空"),
    INTERNAL_ERROR(5001, 500, "系统内部错误"),
    CHANNEL_CALL_FAILED(5002, 500, "通知通道调用失败"),
    RECORD_NOT_FOUND(5003, 404, "发送记录不存在"),
    RECORD_ALREADY_SENT(5004, 409, "该记录今日已成功发送，无需补发");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getDefaultMessage() { return defaultMessage; }
}
