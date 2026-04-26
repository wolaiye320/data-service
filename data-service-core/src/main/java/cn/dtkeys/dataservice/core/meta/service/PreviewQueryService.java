package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceConflictException;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import cn.dtkeys.dataservice.core.security.CredentialCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Service
public class PreviewQueryService {

    private static final TypeReference<List<DomaSqlTemplateParser.ParamSnapshot>> PARAM_SNAPSHOT_LIST =
            new TypeReference<>() {
            };

    private final DsConnectionRepository connectionRepository;
    private final CredentialCodec credentialCodec;
    private final DialectRuleService dialectRuleService;
    private final ObjectMapper objectMapper;
    private final ReadOnlySqlGuard readOnlySqlGuard;

    public PreviewQueryService(DsConnectionRepository connectionRepository,
                               CredentialCodec credentialCodec,
                               DialectRuleService dialectRuleService,
                               ObjectMapper objectMapper,
                               ReadOnlySqlGuard readOnlySqlGuard) {
        this.connectionRepository = connectionRepository;
        this.credentialCodec = credentialCodec;
        this.dialectRuleService = dialectRuleService;
        this.objectMapper = objectMapper;
        this.readOnlySqlGuard = readOnlySqlGuard;
    }

    /**
     * 执行受控预览查询，当前仅支持 SIMPLE_SQL。
     */
    public PreviewQueryResult execute(DsServiceRecord service,
                                      DsServiceVersionRecord draft,
                                      Map<String, Object> previewParams) {
        if (!"SIMPLE_SQL".equals(draft.getSqlType())) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "当前预览执行暂仅支持 SIMPLE_SQL");
        }
        if (draft.getSqlText().contains("/*%if") || draft.getSqlText().contains("/*%for")) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "当前预览执行暂不支持 Doma 条件块");
        }
        DsConnectionRecord connection = requireEnabledConnection(service.getDefaultConnectionCode());
        String renderedSql = renderSql(draft.getSqlText(), draft.getParamSnapshotJson(), previewParams);
        DialectRuleService.RewriteResult rewriteResult = dialectRuleService.rewriteSql(renderedSql, connection);
        readOnlySqlGuard.validate(rewriteResult.sql());
        long startedAt = System.currentTimeMillis();
        try (Connection jdbcConnection = openConnection(connection);
             PreparedStatement statement = jdbcConnection.prepareStatement(rewriteResult.sql())) {
            int maxResultRows = service.getMaxResultRows();
            statement.setQueryTimeout(service.getQueryTimeoutSeconds());
            statement.setMaxRows(maxResultRows + 1);
            statement.setFetchSize(Math.min(maxResultRows + 1, 200));
            try (ResultSet resultSet = statement.executeQuery()) {
                PreviewRows previewRows = extractRows(resultSet, maxResultRows);
                long elapsedMs = System.currentTimeMillis() - startedAt;
                Map<String, Object> diagnosticSummary = new LinkedHashMap<>();
                diagnosticSummary.put("connectionCode", connection.getConnectionCode());
                diagnosticSummary.put("sqlType", draft.getSqlType());
                diagnosticSummary.put("rowCount", previewRows.rows().size());
                diagnosticSummary.put("truncated", previewRows.truncated());
                diagnosticSummary.put("timeoutSeconds", service.getQueryTimeoutSeconds());
                diagnosticSummary.put("maxResultRows", maxResultRows);
                diagnosticSummary.put("dialectRuleCount", rewriteResult.effectiveRules().size());
                diagnosticSummary.put("dialectRewriteApplied", rewriteResult.appliedRuleCount() > 0);
                return new PreviewQueryResult(previewRows.rows(), elapsedMs, diagnosticSummary);
            }
        } catch (SQLException ex) {
            if (isTimeoutException(ex)) {
                throw cn.dtkeys.dataservice.core.error.ResourceProtectionException.queryTimeoutExceeded(
                        "SINGLE_SOURCE_EXECUTION",
                        service.getQueryTimeoutSeconds()
                );
            }
            throw new DataServiceException(ErrorCode.DATASOURCE_CONNECTION_TEST_FAILED, "预览执行失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 按发布参数快照将参数转换为 JDBC 绑定值，供正式查询链路复用。
     */
    public PreviewQueryResult executeBound(DsServiceRecord service,
                                          DsServiceVersionRecord draft,
                                          Map<String, Object> boundParams) {
        return executeBound(service, draft, boundParams, "SINGLE_SOURCE_EXECUTION");
    }

    public PreviewQueryResult executeBound(DsServiceRecord service,
                                           DsServiceVersionRecord draft,
                                           Map<String, Object> boundParams,
                                           String timeoutStage) {
        if (!"SIMPLE_SQL".equals(draft.getSqlType())) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "当前预览执行暂仅支持 SIMPLE_SQL");
        }
        if (draft.getSqlText().contains("/*%if") || draft.getSqlText().contains("/*%for")) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "当前预览执行暂不支持 Doma 条件块");
        }
        DsConnectionRecord connection = requireEnabledConnection(service.getDefaultConnectionCode());
        PreparedSql preparedSql = toPreparedSql(draft.getSqlText(), draft.getParamSnapshotJson(), boundParams);
        DialectRuleService.RewriteResult rewriteResult = dialectRuleService.rewriteSql(preparedSql.sql(), connection);
        readOnlySqlGuard.validate(rewriteResult.sql());
        long startedAt = System.currentTimeMillis();
        try (Connection jdbcConnection = openConnection(connection);
             PreparedStatement statement = jdbcConnection.prepareStatement(rewriteResult.sql())) {
            bindStatement(statement, preparedSql.bindValues());
            int maxResultRows = service.getMaxResultRows();
            statement.setQueryTimeout(service.getQueryTimeoutSeconds());
            statement.setMaxRows(maxResultRows + 1);
            statement.setFetchSize(Math.min(maxResultRows + 1, 200));
            try (ResultSet resultSet = statement.executeQuery()) {
                PreviewRows previewRows = extractRows(resultSet, maxResultRows);
                long elapsedMs = System.currentTimeMillis() - startedAt;
                Map<String, Object> diagnosticSummary = new LinkedHashMap<>();
                diagnosticSummary.put("connectionCode", connection.getConnectionCode());
                diagnosticSummary.put("sqlType", draft.getSqlType());
                diagnosticSummary.put("rowCount", previewRows.rows().size());
                diagnosticSummary.put("truncated", previewRows.truncated());
                diagnosticSummary.put("timeoutSeconds", service.getQueryTimeoutSeconds());
                diagnosticSummary.put("maxResultRows", maxResultRows);
                diagnosticSummary.put("dialectRuleCount", rewriteResult.effectiveRules().size());
                diagnosticSummary.put("dialectRewriteApplied", rewriteResult.appliedRuleCount() > 0);
                diagnosticSummary.put("boundParamCount", preparedSql.bindValues().size());
                return new PreviewQueryResult(previewRows.rows(), elapsedMs, diagnosticSummary);
            }
        } catch (SQLException ex) {
            if (isTimeoutException(ex)) {
                throw cn.dtkeys.dataservice.core.error.ResourceProtectionException.queryTimeoutExceeded(
                        timeoutStage,
                        service.getQueryTimeoutSeconds()
                );
            }
            throw new DataServiceException(ErrorCode.DATASOURCE_CONNECTION_TEST_FAILED, "预览执行失败: " + ex.getMessage(), ex);
        }
    }

    private boolean isTimeoutException(SQLException ex) {
        if (ex == null) {
            return false;
        }
        if ("57014".equals(ex.getSQLState())) {
            return true;
        }
        String message = ex.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("canceling statement due to user request")) {
            return true;
        }
        return isTimeoutException(ex.getNextException());
    }

    private String renderSql(String sqlText, String paramSnapshotJson, Map<String, Object> previewParams) {
        String rendered = sqlText;
        Map<String, Object> params = previewParams == null ? Map.of() : previewParams;
        for (DomaSqlTemplateParser.ParamSnapshot snapshot : parseSnapshots(paramSnapshotJson)) {
            String replacement = buildReplacement(snapshot, params.get(snapshot.paramName()));
            rendered = replacePlaceholder(rendered, snapshot, replacement);
        }
        return rendered;
    }

    private PreparedSql toPreparedSql(String sqlText, String paramSnapshotJson, Map<String, Object> params) {
        String preparedSql = sqlText;
        List<Object> bindValues = new ArrayList<>();
        Map<String, Object> normalizedParams = params == null ? Map.of() : params;
        for (DomaSqlTemplateParser.ParamSnapshot snapshot : parseSnapshots(paramSnapshotJson)) {
            Object value = normalizedParams.get(snapshot.paramName());
            PreparedReplacement replacement = buildPreparedReplacement(snapshot, value);
            preparedSql = replacePlaceholder(preparedSql, snapshot, replacement.sqlFragment());
            bindValues.addAll(replacement.bindValues());
        }
        return new PreparedSql(preparedSql, bindValues);
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

    private String buildReplacement(DomaSqlTemplateParser.ParamSnapshot snapshot, Object value) {
        if (value == null) {
            return snapshot.defaultValue();
        }
        if (snapshot.collection()) {
            return renderCollection(snapshot.paramType(), value);
        }
        return renderScalar(snapshot.paramName(), snapshot.paramType(), value);
    }

    private PreparedReplacement buildPreparedReplacement(DomaSqlTemplateParser.ParamSnapshot snapshot, Object value) {
        if (value == null) {
            return new PreparedReplacement("null", List.of());
        }
        if (snapshot.collection()) {
            List<?> values = toList(value);
            List<String> placeholders = new ArrayList<>(values.size());
            List<Object> bindValues = new ArrayList<>(values.size());
            for (Object item : values) {
                placeholders.add("?");
                bindValues.add(item);
            }
            return new PreparedReplacement("(" + String.join(", ", placeholders) + ")", bindValues);
        }
        return new PreparedReplacement("?", List.of(value));
    }

    private String renderCollection(String paramType, Object value) {
        List<?> values = toList(value);
        List<String> literals = values.stream()
                .map(item -> renderScalar(null, paramType, item))
                .toList();
        return "(" + String.join(", ", literals) + ")";
    }

    private List<?> toList(Object value) {
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
        throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览参数必须是集合: " + value);
    }

    private String renderScalar(String paramName, String paramType, Object value) {
        if (value == null) {
            return "null";
        }
        String normalizedType = paramType == null ? "" : paramType.toUpperCase(Locale.ROOT);
        return switch (normalizedType) {
            case "LONG" -> Long.toString(toLong(paramName, value));
            case "INTEGER", "INT" -> Integer.toString(toInteger(paramName, value));
            case "DOUBLE" -> Double.toString(toDouble(paramName, value));
            case "DECIMAL", "FLOAT" -> value.toString();
            case "BOOLEAN" -> Boolean.toString(toBoolean(paramName, value));
            case "DATE", "DATETIME", "TIMESTAMP", "LOCAL_DATE", "LOCAL_DATE_TIME", "STRING", "" ->
                    quoteString(value instanceof TemporalAccessor ? value.toString() : String.valueOf(value));
            default -> throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不支持的预览参数类型: " + paramType);
        };
    }

    private long toLong(String paramName, Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览参数类型不匹配: " + paramName, ex);
        }
    }

    private int toInteger(String paramName, Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览参数类型不匹配: " + paramName, ex);
        }
    }

    private double toDouble(String paramName, Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览参数类型不匹配: " + paramName, ex);
        }
    }

    private boolean toBoolean(String paramName, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if ("true".equalsIgnoreCase(String.valueOf(value)) || "false".equalsIgnoreCase(String.valueOf(value))) {
            return Boolean.parseBoolean(String.valueOf(value));
        }
        throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "预览参数类型不匹配: " + paramName);
    }

    private String quoteString(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private void bindStatement(PreparedStatement statement, List<Object> bindValues) throws SQLException {
        for (int index = 0; index < bindValues.size(); index++) {
            bindValue(statement, index + 1, bindValues.get(index));
        }
    }

    private void bindValue(PreparedStatement statement, int parameterIndex, Object value) throws SQLException {
        if (value == null) {
            statement.setObject(parameterIndex, null);
            return;
        }
        if (value instanceof Long longValue) {
            statement.setLong(parameterIndex, longValue);
            return;
        }
        if (value instanceof Integer intValue) {
            statement.setInt(parameterIndex, intValue);
            return;
        }
        if (value instanceof Double doubleValue) {
            statement.setDouble(parameterIndex, doubleValue);
            return;
        }
        if (value instanceof Float floatValue) {
            statement.setFloat(parameterIndex, floatValue);
            return;
        }
        if (value instanceof BigDecimal decimalValue) {
            statement.setBigDecimal(parameterIndex, decimalValue);
            return;
        }
        if (value instanceof Boolean booleanValue) {
            statement.setBoolean(parameterIndex, booleanValue);
            return;
        }
        if (value instanceof LocalDate localDate) {
            statement.setDate(parameterIndex, Date.valueOf(localDate));
            return;
        }
        if (value instanceof LocalDateTime localDateTime) {
            statement.setTimestamp(parameterIndex, Timestamp.valueOf(localDateTime));
            return;
        }
        if (value instanceof TemporalAccessor) {
            statement.setString(parameterIndex, value.toString());
            return;
        }
        statement.setObject(parameterIndex, value);
    }

    private String replacePlaceholder(String sqlText,
                                      DomaSqlTemplateParser.ParamSnapshot snapshot,
                                      String replacement) {
        int placeholderStart = sqlText.indexOf(snapshot.placeholder());
        if (placeholderStart < 0) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 中缺少参数占位符: " + snapshot.paramName());
        }
        int defaultStart = skipWhitespace(sqlText, placeholderStart + snapshot.placeholder().length());
        int defaultEnd = findDefaultValueEnd(sqlText, defaultStart);
        return sqlText.substring(0, placeholderStart) + replacement + sqlText.substring(defaultEnd);
    }

    private int skipWhitespace(String sqlText, int index) {
        int current = index;
        while (current < sqlText.length() && Character.isWhitespace(sqlText.charAt(current))) {
            current++;
        }
        return current;
    }

    private int findDefaultValueEnd(String sqlText, int index) {
        if (index >= sqlText.length()) {
            return index;
        }
        char first = sqlText.charAt(index);
        if (first == '(') {
            int depth = 0;
            for (int i = index; i < sqlText.length(); i++) {
                char current = sqlText.charAt(i);
                if (current == '(') {
                    depth++;
                } else if (current == ')') {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
            }
            return sqlText.length();
        }
        if (first == '\'' || first == '"') {
            for (int i = index + 1; i < sqlText.length(); i++) {
                if (sqlText.charAt(i) == first && sqlText.charAt(i - 1) != '\\') {
                    return i + 1;
                }
            }
            return sqlText.length();
        }
        int current = index;
        while (current < sqlText.length()) {
            char token = sqlText.charAt(current);
            if (Character.isWhitespace(token) || token == ',' || token == ')') {
                return current;
            }
            current++;
        }
        return current;
    }

    private PreviewRows extractRows(ResultSet resultSet, int maxResultRows) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (resultSet.next()) {
            if (rows.size() == maxResultRows) {
                truncated = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return new PreviewRows(rows, truncated);
    }

    private DsConnectionRecord requireEnabledConnection(String connectionCode) {
        DsConnectionRecord connection = connectionRepository.findByConnectionCode(connectionCode);
        if (connection == null) {
            throw new ResourceNotFoundException("默认连接不存在: " + connectionCode);
        }
        if (!"ENABLED".equals(connection.getStatus())) {
            throw new ResourceConflictException(ErrorCode.DATASOURCE_DISABLED_IN_USE, "默认连接未启用: " + connectionCode);
        }
        return connection;
    }

    private Connection openConnection(DsConnectionRecord record) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", record.getUsername());
        properties.setProperty("password", credentialCodec.decrypt(record.getPasswordCiphertext()));
        return DriverManager.getConnection(buildJdbcUrl(record), properties);
    }

    private String buildJdbcUrl(DsConnectionRecord record) {
        String databaseName = extractDatabaseName(record.getConnectionConfigJson());
        String dbType = record.getDbType().toUpperCase(Locale.ROOT);
        return switch (dbType) {
            case "POSTGRESQL" -> "jdbc:postgresql://" + record.getHost() + ":" + record.getPort() + "/" + databaseName;
            case "MYSQL" -> "jdbc:mysql://" + record.getHost() + ":" + record.getPort() + "/" + databaseName;
            case "ORACLE" -> "jdbc:oracle:thin:@" + record.getHost() + ":" + record.getPort() + "/" + databaseName;
            default -> throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "不支持的数据库类型: " + record.getDbType());
        };
    }

    private String extractDatabaseName(String connectionConfigJson) {
        if (connectionConfigJson == null || connectionConfigJson.isBlank()) {
            return "";
        }
        String marker = "\"databaseName\":\"";
        int start = connectionConfigJson.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = connectionConfigJson.indexOf('"', valueStart);
        return valueEnd > valueStart ? connectionConfigJson.substring(valueStart, valueEnd) : "";
    }

    public record PreviewQueryResult(
            List<Map<String, Object>> rows,
            long elapsedMs,
            Map<String, Object> diagnosticSummary
    ) {
    }

    private record PreparedSql(String sql, List<Object> bindValues) {
    }

    private record PreparedReplacement(String sqlFragment, List<Object> bindValues) {
    }

    private record PreviewRows(List<Map<String, Object>> rows, boolean truncated) {
    }
}
