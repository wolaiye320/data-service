package cn.dtkeys.dataservice.infrastructure.executor;

import cn.dtkeys.dataservice.common.exception.QueryExecutionException;
import cn.dtkeys.dataservice.domain.model.DSField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将原始结果集映射为统一输出字段结构。
 */
@Component
public class QueryResultMapper {

    /**
     * 根据字段定义重命名并转换原始结果。
     *
     * @param rawRows 原始结果
     * @param fields 字段定义
     * @return 映射后的结果
     */
    public List<Map<String, Object>> map(List<Map<String, Object>> rawRows, List<DSField> fields) {
        if (fields == null || fields.isEmpty()) {
            return rawRows;
        }

        List<DSField> sortedFields = fields.stream()
            .sorted(Comparator.comparing(DSField::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DSField::getId, Comparator.nullsLast(Long::compareTo)))
            .toList();

        List<Map<String, Object>> mappedRows = new ArrayList<>(rawRows.size());
        for (Map<String, Object> rawRow : rawRows) {
            Map<String, Object> normalizedRow = normalizeRow(rawRow);
            LinkedHashMap<String, Object> mappedRow = new LinkedHashMap<>();
            for (DSField field : sortedFields) {
                String columnKey = buildColumnKey(field);
                if (!normalizedRow.containsKey(columnKey)) {
                    throw new QueryExecutionException("结果集中缺少字段映射列: " + columnKey);
                }
                mappedRow.put(field.getFieldName(), convertValue(normalizedRow.get(columnKey), field.getFieldType()));
            }
            mappedRows.add(mappedRow);
        }
        return mappedRows;
    }

    private Map<String, Object> normalizeRow(Map<String, Object> rawRow) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        rawRow.forEach((key, value) -> normalized.put(normalizeKey(key), value));
        return normalized;
    }

    private String buildColumnKey(DSField field) {
        if (field.getSourceAlias() == null || field.getSourceAlias().isBlank()) {
            return normalizeKey(field.getSourceColumn());
        }
        return normalizeKey(field.getSourceAlias() + "_" + field.getSourceColumn());
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT);
    }

    private Object convertValue(Object value, String fieldType) {
        if (value == null || fieldType == null || fieldType.isBlank()) {
            return value;
        }

        return switch (fieldType.toUpperCase(Locale.ROOT)) {
            case "STRING" -> String.valueOf(value);
            case "INTEGER", "INT" -> ((Number) toNumber(value)).intValue();
            case "LONG", "BIGINT" -> ((Number) toNumber(value)).longValue();
            case "DECIMAL", "NUMERIC" -> toBigDecimal(value);
            case "DOUBLE" -> ((Number) toNumber(value)).doubleValue();
            case "BOOLEAN" -> toBoolean(value);
            case "DATE" -> toLocalDate(value);
            case "DATETIME", "TIMESTAMP" -> toLocalDateTime(value);
            default -> value;
        };
    }

    private Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new QueryExecutionException("字段值无法转换为数值: " + value, exception);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new QueryExecutionException("字段值无法转换为 Decimal: " + value, exception);
        }
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "y", "yes" -> true;
            case "false", "0", "n", "no" -> false;
            default -> throw new QueryExecutionException("字段值无法转换为 Boolean: " + value);
        };
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value));
    }
}
