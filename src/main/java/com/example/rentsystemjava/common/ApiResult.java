package com.example.rentsystemjava.common;

import org.slf4j.MDC;

/**
 * 统一响应结构：{code, message, data, traceId}
 * code=0 成功；非 0 为业务错误码（见 ErrorCode），前端按 code 分支。
 */
public record ApiResult<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data, currentTraceId());
    }

    public static <T> ApiResult<T> fail(int code, String message, T data) {
        return new ApiResult<>(code, message, data, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }
}
