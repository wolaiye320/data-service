package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.QueryParamValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class QueryParamBindingService {

    private static final TypeReference<List<DomaSqlTemplateParser.ParamSnapshot>> PARAM_SNAPSHOT_LIST =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public QueryParamBindingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按发布参数快照校验并绑定查询参数，提前输出缺失、多余、集合形态和类型转换诊断。
     */
    public BoundQueryParams bind(String paramSnapshotJson, Map<String, Object> requestParams) {
        List<DomaSqlTemplateParser.ParamSnapshot> snapshots = parseSnapshots(paramSnapshotJson);
        Map<String, Object> inputParams = requestParams == null ? Map.of() : requestParams;
        List<QueryParamValidationException.ParamDiagnostic> diagnostics = new ArrayList<>();
        Map<String, Object> boundParams = new LinkedHashMap<>();
        Set<String> expectedNames = new LinkedHashSet<>();
        for (DomaSqlTemplateParser.ParamSnapshot snapshot : snapshots) {
            expectedNames.add(snapshot.paramName());
            if (!inputParams.containsKey(snapshot.paramName())) {
                diagnostics.add(diagnostic(
                        "MISSING_PARAMETER",
                        snapshot.paramName(),
                        "缺少查询参数: " + snapshot.paramName(),
                        snapshot.paramType(),
                        "MISSING",
                        snapshot.collection()
                ));
                continue;
            }
            Object rawValue = inputParams.get(snapshot.paramName());
            bindSnapshot(snapshot, rawValue, boundParams, diagnostics);
        }

        for (String inputName : inputParams.keySet()) {
            if (!expectedNames.contains(inputName)) {
                diagnostics.add(diagnostic(
                        "EXTRA_PARAMETER",
                        inputName,
                        "存在未定义查询参数: " + inputName,
                        null,
                        detectActualType(inputParams.get(inputName)),
                        false
                ));
            }
        }

        if (!diagnostics.isEmpty()) {
            throw new QueryParamValidationException("查询参数校验失败", diagnostics);
        }
        return new BoundQueryParams(boundParams);
    }

    private void bindSnapshot(DomaSqlTemplateParser.ParamSnapshot snapshot,
                              Object rawValue,
                              Map<String, Object> boundParams,
                              List<QueryParamValidationException.ParamDiagnostic> diagnostics) {
        if (rawValue == null) {
            diagnostics.add(diagnostic(
                    "NULL_PARAMETER",
                    snapshot.paramName(),
                    "查询参数不能为空: " + snapshot.paramName(),
                    snapshot.paramType(),
                    "NULL",
                    snapshot.collection()
            ));
            return;
        }
        if (snapshot.collection()) {
            bindCollection(snapshot, rawValue, boundParams, diagnostics);
            return;
        }
        if (isCollectionLike(rawValue)) {
            diagnostics.add(diagnostic(
                    "COLLECTION_MISMATCH",
                    snapshot.paramName(),
                    "查询参数集合形态不匹配: " + snapshot.paramName(),
                    snapshot.paramType(),
                    detectActualType(rawValue),
                    false
            ));
            return;
        }
        bindScalar(snapshot, rawValue, boundParams, diagnostics);
    }

    private void bindCollection(DomaSqlTemplateParser.ParamSnapshot snapshot,
                                Object rawValue,
                                Map<String, Object> boundParams,
                                List<QueryParamValidationException.ParamDiagnostic> diagnostics) {
        if (!isCollectionLike(rawValue)) {
            diagnostics.add(diagnostic(
                    "COLLECTION_MISMATCH",
                    snapshot.paramName(),
                    "查询参数集合形态不匹配: " + snapshot.paramName(),
                    snapshot.paramType(),
                    detectActualType(rawValue),
                    true
            ));
            return;
        }
        List<Object> rawItems = toList(rawValue);
        if (rawItems.isEmpty()) {
            diagnostics.add(diagnostic(
                    "EMPTY_COLLECTION",
                    snapshot.paramName(),
                    "集合参数不能为空: " + snapshot.paramName(),
                    snapshot.paramType(),
                    "EMPTY_COLLECTION",
                    true
            ));
            return;
        }
        List<Object> convertedItems = new ArrayList<>(rawItems.size());
        for (Object rawItem : rawItems) {
            try {
                convertedItems.add(convertScalar(snapshot.paramName(), snapshot.paramType(), rawItem));
            } catch (IllegalArgumentException ex) {
                diagnostics.add(diagnostic(
                        "TYPE_MISMATCH",
                        snapshot.paramName(),
                        ex.getMessage(),
                        snapshot.paramType(),
                        detectActualType(rawItem),
                        true
                ));
                return;
            }
        }
        boundParams.put(snapshot.paramName(), convertedItems);
    }

    private void bindScalar(DomaSqlTemplateParser.ParamSnapshot snapshot,
                            Object rawValue,
                            Map<String, Object> boundParams,
                            List<QueryParamValidationException.ParamDiagnostic> diagnostics) {
        try {
            boundParams.put(snapshot.paramName(), convertScalar(snapshot.paramName(), snapshot.paramType(), rawValue));
        } catch (IllegalArgumentException ex) {
            diagnostics.add(diagnostic(
                    "TYPE_MISMATCH",
                    snapshot.paramName(),
                    ex.getMessage(),
                    snapshot.paramType(),
                    detectActualType(rawValue),
                    false
            ));
        }
    }

    private Object convertScalar(String paramName, String paramType, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("查询参数不能为空: " + paramName);
        }
        String normalizedType = paramType == null ? "" : paramType.toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "LONG" -> toLong(paramName, value);
            case "INTEGER", "INT" -> toInteger(paramName, value);
            case "DOUBLE" -> toDouble(paramName, value);
            case "DECIMAL", "FLOAT" -> toBigDecimal(paramName, value);
            case "BOOLEAN" -> toBoolean(paramName, value);
            case "DATE", "LOCAL_DATE" -> toLocalDate(paramName, value);
            case "DATETIME", "TIMESTAMP", "LOCAL_DATE_TIME" -> toLocalDateTime(paramName, value);
            case "STRING", "" -> String.valueOf(value);
            default -> throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不支持的查询参数类型: " + paramType);
        };
    }

    private Long toLong(String paramName, Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
        }
    }

    private Integer toInteger(String paramName, Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
        }
    }

    private Double toDouble(String paramName, Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
        }
    }

    private BigDecimal toBigDecimal(String paramName, Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
        }
    }

    private Boolean toBoolean(String paramName, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
    }

    private LocalDate toLocalDate(String paramName, Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
        }
    }

    private LocalDateTime toLocalDateTime(String paramName, Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("查询参数类型不匹配: " + paramName);
        }
    }

    private String detectActualType(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (isCollectionLike(value)) {
            return "COLLECTION";
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return "INTEGER";
        }
        if (value instanceof Long) {
            return "LONG";
        }
        if (value instanceof Float || value instanceof Double) {
            return "DOUBLE";
        }
        if (value instanceof BigDecimal) {
            return "DECIMAL";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof LocalDate) {
            return "DATE";
        }
        if (value instanceof LocalDateTime) {
            return "DATETIME";
        }
        if (value instanceof CharSequence) {
            return "STRING";
        }
        return value.getClass().getSimpleName().toUpperCase(Locale.ROOT);
    }

    private boolean isCollectionLike(Object value) {
        return value instanceof Iterable<?> || (value != null && value.getClass().isArray());
    }

    private List<Object> toList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(values::add);
            return values;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        return List.of();
    }

    private List<DomaSqlTemplateParser.ParamSnapshot> parseSnapshots(String paramSnapshotJson) {
        if (paramSnapshotJson == null || paramSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(paramSnapshotJson, PARAM_SNAPSHOT_LIST);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "参数快照反序列化失败", ex);
        }
    }

    private QueryParamValidationException.ParamDiagnostic diagnostic(String reasonCode,
                                                                    String paramName,
                                                                    String message,
                                                                    String expectedType,
                                                                    String actualType,
                                                                    boolean collection) {
        return new QueryParamValidationException.ParamDiagnostic(
                reasonCode,
                paramName,
                message,
                expectedType,
                actualType,
                collection
        );
    }

    public record BoundQueryParams(Map<String, Object> params) {
    }
}
