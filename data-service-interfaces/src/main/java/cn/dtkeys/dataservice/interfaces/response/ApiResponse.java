package cn.dtkeys.dataservice.interfaces.response;

import cn.dtkeys.dataservice.common.enums.ErrorCode;

import java.util.Map;

/**
 * 统一响应结构。
 */
public record ApiResponse<T>(boolean success,
                             String code,
                             String message,
                             T data,
                             Map<String, Object> meta) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), data, Map.of());
    }

    public static <T> ApiResponse<T> success(T data, Map<String, Object> meta) {
        return new ApiResponse<>(true, ErrorCode.OK.name(), ErrorCode.OK.defaultMessage(), data, meta);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.name(), message, null, Map.of());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message, Map<String, Object> meta) {
        return new ApiResponse<>(false, errorCode.name(), message, null, meta);
    }
}
