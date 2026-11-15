package com.example.rentsystemjava.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理：业务码 → HTTP 状态映射；数据库约束冲突转友好业务码，不透出堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Object>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        if (ec.getHttpStatus() >= 500) {
            log.error("业务异常 code={} data={}", ec.getCode(), String.valueOf(ex.getData()), ex);
        } else {
            log.warn("业务异常 code={} msg={} data={}", ec.getCode(), ex.getMessage(), String.valueOf(ex.getData()));
        }
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResult.fail(ec.getCode(), ex.getMessage(), ex.getData()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResult<Map<String, Object>>> handleValidation(BindException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (var fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fields", fields);
        String first = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("参数校验失败");
        return ResponseEntity.status(422).body(ApiResult.fail(3001, first, data));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = String.valueOf(ex.getMessage());
        if (detail.contains("uk_house_location")) {
            return conflict(ErrorCode.HOUSE_LOCATION_DUPLICATE);
        }
        if (detail.contains("uk_contract_active")) {
            return ResponseEntity.status(409)
                    .body(ApiResult.fail(2002, "房源已出租，不能重复登记", null));
        }
        log.error("数据库约束冲突", ex);
        return ResponseEntity.status(500)
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Object>> handleUnknown(Exception ex) {
        log.error("未捕获异常", ex);
        return ResponseEntity.status(500)
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null));
    }

    /** 缺失的静态资源（如 favicon.ico）应返回 404，而非被兜底异常吞成 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(ApiResult.fail(404, "资源不存在", null));
    }

    private ResponseEntity<ApiResult<Object>> conflict(ErrorCode ec) {
        log.warn("数据库约束冲突 → code={} {}", ec.getCode(), ec.getDefaultMessage());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiResult.fail(ec.getCode(), ec.getDefaultMessage(), null));
    }
}
