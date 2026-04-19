package cn.dtkeys.dataservice.web.dto.admin.service;

import java.util.List;

public record SqlAutoDetectResponse(
    String sqlType,
    String serviceType,
    String executionMode,
    String planStatus,
    List<DetectedSourceView> sources,
    List<DetectedParamView> params,
    List<DetectedFieldView> fields
) {

    public record DetectedSourceView(
        Long connectionId,
        Long catalogId,
        String sourceAlias,
        String sourceType,
        String sourceValue,
        String sourceName,
        String sqlAlias,
        boolean connectionResolved,
        boolean catalogResolved
    ) {
    }

    public record DetectedParamView(
        String paramName,
        String displayName,
        String paramType,
        String sqlPlaceholder,
        boolean required,
        int sortOrder
    ) {
    }

    public record DetectedFieldView(
        String sourceAlias,
        String sourceColumn,
        String fieldName,
        String displayName,
        String fieldType,
        int sortOrder,
        boolean primaryKey,
        boolean joinKey,
        String selectedExpression
    ) {
    }
}
