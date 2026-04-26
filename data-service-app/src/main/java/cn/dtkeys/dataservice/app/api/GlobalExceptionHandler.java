package cn.dtkeys.dataservice.app.api;

import cn.dtkeys.dataservice.core.error.AccessDeniedException;
import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceConflictException;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.error.ResourceProtectionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, message, null, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ResourceConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(DataServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(DataServiceException ex, HttpServletRequest request) {
        log.warn("business_error code={} message={}", ex.getErrorCode(), ex.getMessage(), ex);
        HttpStatus status = switch (ex.getErrorCode()) {
            case INVALID_ARGUMENT, DATASOURCE_CONNECTION_TEST_FAILED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return build(
                status,
                ex.getErrorCode(),
                ex.getMessage(),
                ex instanceof ResourceProtectionException resourceProtectionException
                        ? resourceProtectionException.getDiagnosticSummary()
                        : null,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unexpected_error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "系统内部错误", null, request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status,
                                                   ErrorCode errorCode,
                                                   String message,
                                                   java.util.Map<String, Object> diagnosticSummary,
                                                   HttpServletRequest request) {
        String traceId = (String) request.getAttribute(AdminRequestContext.ATTRIBUTE_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(AdminRequestContext.TRACE_ID_HEADER);
        }
        return ResponseEntity.status(status).body(new ApiErrorResponse(errorCode.name(), message, traceId, diagnosticSummary));
    }

    private String toMessage(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
