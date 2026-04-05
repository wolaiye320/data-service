package cn.dtkeys.dataservice.interfaces.controller;

import cn.dtkeys.dataservice.common.enums.ErrorCode;
import cn.dtkeys.dataservice.common.context.TraceContext;
import cn.dtkeys.dataservice.common.exception.DataServiceException;
import cn.dtkeys.dataservice.interfaces.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常映射。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataServiceException(DataServiceException exception,
                                                                       HttpServletRequest request) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case SERVICE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SERVICE_DISABLED, SERVICE_CONFIG_INVALID -> HttpStatus.CONFLICT;
            case DATASOURCE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            case RESOURCE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case QUERY_TIMEOUT -> HttpStatus.REQUEST_TIMEOUT;
            case QUERY_EXECUTION_FAILED, AUDIT_LOG_WRITE_FAILED, AUDIT_LOG_QUERY_FAILED, INTERNAL_ERROR ->
                HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        log.warn("business_exception path={} code={} message={}",
            request.getRequestURI(), exception.getErrorCode().name(), exception.getMessage());
        return ResponseEntity.status(status)
            .body(ApiResponse.failure(exception.getErrorCode(), exception.getMessage(), buildMeta()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class,
        IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception,
                                                                      HttpServletRequest request) {
        String message = switch (exception) {
            case MethodArgumentNotValidException ex -> ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
            case BindException ex -> ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
            case ConstraintViolationException ex -> ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
            case IllegalArgumentException ex -> ex.getMessage();
            default -> ErrorCode.PARAM_INVALID.defaultMessage();
        };
        log.warn("validation_exception path={} message={}", request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.PARAM_INVALID, message, buildMeta()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception exception, HttpServletRequest request) {
        log.error("system_exception path={}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), buildMeta()));
    }

    private Map<String, Object> buildMeta() {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        TraceContext.getTraceId().ifPresent(traceId -> meta.put("traceId", traceId));
        return meta;
    }
}
