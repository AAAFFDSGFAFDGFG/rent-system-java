package com.example.rentsystemjava.common;

/**
 * 业务异常：Service 层校验/状态机失败时抛出，由 GlobalExceptionHandler 统一转为响应。
 * data 可携带业务负载（如 2006 的违约预览、3001 的字段明细）。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Object getData() { return data; }
}
