package cn.dtkeys.dataservice.query.executor;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.service.model.DSParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 负责参数一致性检查、默认值回填与类型转换。
 */
@Component
public class QueryParameterBinder {

    /**
     * 根据参数定义和请求参数构建绑定参数。
     *
     * @param sql SQL 模板
     * @param paramDefinitions 参数定义
     * @param requestParams 请求参数
     * @return 绑定后的 SQL 与参数
     */
    public BoundQuery bind(String sql, List<DSParam> paramDefinitions, Map<String, Object> requestParams) {
        Map<String, Object> safeRequestParams = requestParams == null ? Map.of() : requestParams;
        SqlTemplateRenderer.RenderedSql renderedSql = SqlTemplateRenderer.render(sql, safeRequestParams);
        Set<String> placeholders = renderedSql.placeholders();
        Set<String> allPlaceholders = SqlTemplateRenderer.extractPlaceholders(sql);

        Map<String, DSParam> definitionsByPlaceholder = paramDefinitions.stream()
            .collect(Collectors.toMap(DSParam::getSqlPlaceholder, definition -> definition, (left, right) -> left,
                LinkedHashMap::new));

        if (!definitionsByPlaceholder.keySet().equals(allPlaceholders)) {
            throw new ParamInvalidException("参数定义与 SQL 占位符不一致");
        }

        LinkedHashMap<String, Object> boundParams = new LinkedHashMap<>();
        for (DSParam definition : paramDefinitions) {
            if (!placeholders.contains(definition.getSqlPlaceholder())) {
                continue;
            }
            Object rawValue = safeRequestParams.get(definition.getParamName());
            if (isEmpty(rawValue) && definition.getDefaultValue() != null && !definition.getDefaultValue().isBlank()) {
                rawValue = definition.getDefaultValue();
            }

            if (Boolean.TRUE.equals(definition.getRequired()) && isEmpty(rawValue)) {
                throw new ParamInvalidException("缺少必填参数: " + definition.getParamName());
            }
            if (rawValue == null) {
                boundParams.put(definition.getSqlPlaceholder(), null);
                continue;
            }
            boundParams.put(definition.getSqlPlaceholder(), convertValue(definition, rawValue));
        }

        Set<String> unknownParams = safeRequestParams.keySet().stream()
            .filter(key -> paramDefinitions.stream().noneMatch(definition -> definition.getParamName().equals(key)))
            .collect(Collectors.toSet());
        if (!unknownParams.isEmpty()) {
            throw new ParamInvalidException("存在未定义参数: " + String.join(",", unknownParams));
        }

        return new BoundQuery(renderedSql.sql(), Map.copyOf(boundParams));
    }

    private Object convertValue(DSParam definition, Object rawValue) {
        String paramType = definition.getParamType() == null ? "STRING" : definition.getParamType().toUpperCase(Locale.ROOT);
        return switch (paramType) {
            case "STRING" -> String.valueOf(rawValue);
            case "INTEGER", "INT" -> toInteger(definition, rawValue);
            case "LONG", "BIGINT" -> toLong(definition, rawValue);
            case "DECIMAL", "NUMERIC" -> toDecimal(definition, rawValue);
            case "BOOLEAN" -> toBoolean(definition, rawValue);
            case "DATE" -> toDate(definition, rawValue);
            case "DATETIME", "TIMESTAMP" -> toDateTime(definition, rawValue);
            case "LIST", "ARRAY" -> toCollection(definition, rawValue);
            default -> throw new ParamInvalidException("不支持的参数类型: " + definition.getParamType());
        };
    }

    private boolean isEmpty(Object value) {
        return value == null
            || value instanceof String text && text.isBlank()
            || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private Integer toInteger(DSParam definition, Object rawValue) {
        try {
            if (rawValue instanceof Number number) {
                return number.intValue();
            }
            return Integer.valueOf(String.valueOf(rawValue));
        } catch (Exception exception) {
            throw invalidType(definition, rawValue, "INTEGER", exception);
        }
    }

    private Long toLong(DSParam definition, Object rawValue) {
        try {
            if (rawValue instanceof Number number) {
                return number.longValue();
            }
            return Long.valueOf(String.valueOf(rawValue));
        } catch (Exception exception) {
            throw invalidType(definition, rawValue, "LONG", exception);
        }
    }

    private BigDecimal toDecimal(DSParam definition, Object rawValue) {
        try {
            if (rawValue instanceof BigDecimal decimal) {
                return decimal;
            }
            return new BigDecimal(String.valueOf(rawValue));
        } catch (Exception exception) {
            throw invalidType(definition, rawValue, "DECIMAL", exception);
        }
    }

    private Boolean toBoolean(DSParam definition, Object rawValue) {
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(rawValue).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "y", "yes" -> true;
            case "false", "0", "n", "no" -> false;
            default -> throw invalidType(definition, rawValue, "BOOLEAN", null);
        };
    }

    private LocalDate toDate(DSParam definition, Object rawValue) {
        try {
            if (rawValue instanceof LocalDate date) {
                return date;
            }
            return LocalDate.parse(String.valueOf(rawValue));
        } catch (Exception exception) {
            throw invalidType(definition, rawValue, "DATE", exception);
        }
    }

    private LocalDateTime toDateTime(DSParam definition, Object rawValue) {
        try {
            if (rawValue instanceof LocalDateTime dateTime) {
                return dateTime;
            }
            return LocalDateTime.parse(String.valueOf(rawValue));
        } catch (Exception exception) {
            throw invalidType(definition, rawValue, "DATETIME", exception);
        }
    }

    private Collection<?> toCollection(DSParam definition, Object rawValue) {
        if (rawValue instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw new ParamInvalidException("集合参数不能为空: " + definition.getParamName());
            }
            return collection;
        }
        throw invalidType(definition, rawValue, "LIST", null);
    }

    private ParamInvalidException invalidType(DSParam definition, Object rawValue, String targetType, Exception cause) {
        String message = "参数类型不匹配: " + definition.getParamName() + " 需要 " + targetType + "，实际值=" + rawValue;
        return cause == null ? new ParamInvalidException(message) : new ParamInvalidException(message);
    }
}
