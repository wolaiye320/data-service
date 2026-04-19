package cn.dtkeys.dataservice.service;

import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.federation.model.FederatedParsedQuery;
import cn.dtkeys.dataservice.federation.model.FederatedSourceReference;
import cn.dtkeys.dataservice.federation.parser.FederatedSqlParser;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.web.dto.admin.service.SqlAutoDetectResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据 SQL 自动识别来源、参数与字段，供“SQL 即服务”创建流程复用。
 */
@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class SqlAutoDetectService {

    private static final Pattern PARAMETER_PATTERN = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");

    private final DSConnectionRepository dsConnectionRepository;
    private final DSCatalogRepository dsCatalogRepository;
    private final FederatedSqlParser federatedSqlParser;

    public SqlAutoDetectService(DSConnectionRepository dsConnectionRepository,
                                DSCatalogRepository dsCatalogRepository) {
        this.dsConnectionRepository = dsConnectionRepository;
        this.dsCatalogRepository = dsCatalogRepository;
        this.federatedSqlParser = new FederatedSqlParser();
    }

    public SqlAutoDetectResponse detect(String sqlText,
                                        String requestedSqlType,
                                        Long defaultConnectionId,
                                        Long defaultCatalogId) {
        if (sqlText == null || sqlText.isBlank()) {
            throw new ParamInvalidException("sqlText 不能为空");
        }
        String sqlType = normalizeSqlType(requestedSqlType, sqlText);
        FederatedParsedQuery parsedQuery = parse(sqlText);
        String serviceType = "FEDERATED_SQL".equals(sqlType) ? "FEDERATED_QUERY" : "SIMPLE_QUERY";
        String executionMode = parsedQuery.joinQuery() || parsedQuery.sourceTables().size() > 1
            ? "REMOTE_PLUS_LOCAL"
            : "REMOTE_ONLY";
        String planStatus = "FEDERATED_SQL".equals(sqlType) ? "PLANNED" : "UNPLANNED";
        ConnectionResolutionContext resolutionContext = buildResolutionContext(defaultConnectionId, defaultCatalogId);

        List<SqlAutoDetectResponse.DetectedSourceView> sources = detectSources(parsedQuery, resolutionContext);
        AliasResolution aliasResolution = buildAliasResolution(parsedQuery.sourceReferences());
        List<SqlAutoDetectResponse.DetectedParamView> params = detectParams(sqlText);
        List<SqlAutoDetectResponse.DetectedFieldView> fields = detectFields(parsedQuery, aliasResolution);
        return new SqlAutoDetectResponse(sqlType, serviceType, executionMode, planStatus, sources, params, fields);
    }

    private FederatedParsedQuery parse(String sqlText) {
        try {
            return federatedSqlParser.parse(sqlText);
        } catch (IllegalArgumentException exception) {
            throw new ParamInvalidException("SQL 自动识别失败: " + exception.getMessage());
        }
    }

    private String normalizeSqlType(String requestedSqlType, String sqlText) {
        if (requestedSqlType != null && !requestedSqlType.isBlank()) {
            String normalized = requestedSqlType.trim().toUpperCase(Locale.ROOT);
            if ("SIMPLE_SQL".equals(normalized) || "FEDERATED_SQL".equals(normalized)) {
                return normalized;
            }
            throw new ParamInvalidException("不支持的 sqlType: " + requestedSqlType);
        }
        String normalizedSql = sqlText.trim().toUpperCase(Locale.ROOT);
        return normalizedSql.contains(" JOIN ") ? "FEDERATED_SQL" : "SIMPLE_SQL";
    }

    private ConnectionResolutionContext buildResolutionContext(Long defaultConnectionId, Long defaultCatalogId) {
        List<DSConnection> connections = dsConnectionRepository.findAll();
        Map<Long, DSConnection> connectionById = new LinkedHashMap<>();
        Map<Long, List<DSCatalog>> catalogsByConnectionId = new LinkedHashMap<>();
        for (DSConnection connection : connections) {
            connectionById.put(connection.getId(), connection);
            catalogsByConnectionId.put(connection.getId(), dsCatalogRepository.findByConnectionId(connection.getId()));
        }
        DSConnection defaultConnection = resolveDefaultConnection(defaultConnectionId, connections, connectionById);
        DSCatalog defaultCatalog = resolveDefaultCatalog(defaultCatalogId, defaultConnection, catalogsByConnectionId);
        return new ConnectionResolutionContext(connectionById, catalogsByConnectionId, defaultConnection, defaultCatalog);
    }

    private DSConnection resolveDefaultConnection(Long defaultConnectionId,
                                                  List<DSConnection> connections,
                                                  Map<Long, DSConnection> connectionById) {
        if (defaultConnectionId != null) {
            DSConnection connection = connectionById.get(defaultConnectionId);
            if (connection == null) {
                throw new ParamInvalidException("默认连接不存在: " + defaultConnectionId);
            }
            return connection;
        }
        return connections.size() == 1 ? connections.get(0) : null;
    }

    private DSCatalog resolveDefaultCatalog(Long defaultCatalogId,
                                            DSConnection defaultConnection,
                                            Map<Long, List<DSCatalog>> catalogsByConnectionId) {
        if (defaultCatalogId != null) {
            DSCatalog catalog = dsCatalogRepository.findById(defaultCatalogId);
            if (catalog == null) {
                throw new ParamInvalidException("默认目录不存在: " + defaultCatalogId);
            }
            if (defaultConnection != null && !Objects.equals(catalog.getConnectionId(), defaultConnection.getId())) {
                throw new ParamInvalidException("默认目录不属于默认连接");
            }
            return catalog;
        }
        if (defaultConnection == null) {
            return null;
        }
        List<DSCatalog> catalogs = catalogsByConnectionId.getOrDefault(defaultConnection.getId(), List.of());
        return catalogs.size() == 1 ? catalogs.get(0) : null;
    }

    private List<SqlAutoDetectResponse.DetectedSourceView> detectSources(FederatedParsedQuery parsedQuery,
                                                                         ConnectionResolutionContext context) {
        List<SqlAutoDetectResponse.DetectedSourceView> sources = new ArrayList<>();
        for (FederatedSourceReference sourceReference : parsedQuery.sourceReferences()) {
            String sourceValue = stripSchemaPrefix(sourceReference.sourceName());
            String sourceAlias = sourceReference.sqlAlias() == null || sourceReference.sqlAlias().isBlank()
                ? sourceValue
                : sourceReference.sqlAlias();
            Long connectionId = context.defaultConnection() == null ? null : context.defaultConnection().getId();
            Long catalogId = context.defaultCatalog() == null ? null : context.defaultCatalog().getId();
            sources.add(new SqlAutoDetectResponse.DetectedSourceView(
                connectionId,
                catalogId,
                sourceAlias,
                "TABLE",
                sourceValue,
                sourceReference.sourceName(),
                sourceReference.sqlAlias(),
                connectionId != null,
                catalogId != null
            ));
        }
        return List.copyOf(sources);
    }

    private AliasResolution buildAliasResolution(List<FederatedSourceReference> sourceReferences) {
        Map<String, String> aliasToSourceAlias = new LinkedHashMap<>();
        Set<String> knownNames = new LinkedHashSet<>();
        for (FederatedSourceReference sourceReference : sourceReferences) {
            String sourceAlias = sourceReference.sqlAlias() == null || sourceReference.sqlAlias().isBlank()
                ? stripSchemaPrefix(sourceReference.sourceName())
                : sourceReference.sqlAlias();
            aliasToSourceAlias.put(normalize(sourceReference.sourceName()), sourceAlias);
            aliasToSourceAlias.put(normalize(stripSchemaPrefix(sourceReference.sourceName())), sourceAlias);
            aliasToSourceAlias.put(normalize(sourceAlias), sourceAlias);
            knownNames.add(normalize(sourceAlias));
            knownNames.add(normalize(sourceReference.sourceName()));
            knownNames.add(normalize(stripSchemaPrefix(sourceReference.sourceName())));
        }
        return new AliasResolution(aliasToSourceAlias, knownNames);
    }

    private List<SqlAutoDetectResponse.DetectedParamView> detectParams(String sqlText) {
        String sanitizedSql = sqlText.replaceAll("'([^']|'')*'", " ");
        Matcher matcher = PARAMETER_PATTERN.matcher(sanitizedSql);
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        List<SqlAutoDetectResponse.DetectedParamView> params = new ArrayList<>();
        int index = 1;
        for (String placeholder : placeholders) {
            params.add(new SqlAutoDetectResponse.DetectedParamView(
                placeholder,
                placeholder,
                inferParamType(placeholder),
                placeholder,
                true,
                index++
            ));
        }
        return List.copyOf(params);
    }

    private List<SqlAutoDetectResponse.DetectedFieldView> detectFields(FederatedParsedQuery parsedQuery,
                                                                       AliasResolution aliasResolution) {
        List<SqlAutoDetectResponse.DetectedFieldView> fields = new ArrayList<>();
        int index = 1;
        for (String selectedField : parsedQuery.selectedFields()) {
            if ("*".equals(selectedField.trim())) {
                continue;
            }
            SelectedField selected = parseSelectedField(selectedField, aliasResolution);
            fields.add(new SqlAutoDetectResponse.DetectedFieldView(
                selected.sourceAlias(),
                selected.sourceColumn(),
                selected.fieldName(),
                selected.fieldName(),
                inferFieldType(selected.fieldName(), selected.sourceColumn()),
                index++,
                index == 2,
                selected.joinKey(),
                selectedField
            ));
        }
        return List.copyOf(fields);
    }

    private SelectedField parseSelectedField(String selectedField, AliasResolution aliasResolution) {
        String trimmed = selectedField.trim();
        String outputName = extractOutputName(trimmed);
        String expression = removeAliasPart(trimmed);
        String normalizedExpression = expression.trim();
        if (normalizedExpression.contains("(")) {
            return new SelectedField(null, outputName, outputName, false);
        }
        String sourceAlias = null;
        String sourceColumn = normalizedExpression;
        int dotIndex = normalizedExpression.indexOf('.');
        if (dotIndex >= 0) {
            String qualifier = normalizedExpression.substring(0, dotIndex).trim();
            sourceAlias = aliasResolution.aliasToSourceAlias().getOrDefault(normalize(qualifier), qualifier);
            sourceColumn = normalizedExpression.substring(dotIndex + 1).trim();
        } else if (aliasResolution.knownNames().size() == 1) {
            sourceAlias = aliasResolution.aliasToSourceAlias().values().stream().findFirst().orElse(null);
        }
        String normalizedColumn = unquote(sourceColumn);
        String fieldName = toCamelCase(outputName);
        boolean joinKey = looksLikeJoinKey(fieldName, normalizedColumn);
        return new SelectedField(sourceAlias, normalizedColumn, fieldName, joinKey);
    }

    private String extractOutputName(String selectedField) {
        String trimmed = selectedField.trim();
        Matcher matcher = Pattern.compile("(?is)\\s+AS\\s+([A-Za-z_][A-Za-z0-9_]*)$").matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length > 1) {
            return tokens[tokens.length - 1].trim();
        }
        int dotIndex = trimmed.lastIndexOf('.');
        return dotIndex >= 0 ? unquote(trimmed.substring(dotIndex + 1).trim()) : unquote(trimmed);
    }

    private String removeAliasPart(String selectedField) {
        Matcher matcher = Pattern.compile("(?is)^(.*?)\\s+AS\\s+[A-Za-z_][A-Za-z0-9_]*$").matcher(selectedField.trim());
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        String[] tokens = selectedField.trim().split("\\s+");
        if (tokens.length > 1) {
            return String.join(" ", java.util.Arrays.copyOf(tokens, tokens.length - 1));
        }
        return selectedField.trim();
    }

    private String inferParamType(String placeholder) {
        String normalized = normalize(placeholder);
        if (normalized.endsWith("id") || normalized.endsWith("ids")) {
            return normalized.endsWith("ids") ? "LIST" : "LONG";
        }
        if (normalized.startsWith("is") || normalized.startsWith("has") || normalized.contains("enabled")
            || normalized.contains("active")) {
            return "BOOLEAN";
        }
        if (normalized.contains("count") || normalized.contains("num") || normalized.contains("amount")
            || normalized.contains("price")) {
            return "DECIMAL";
        }
        if (normalized.contains("date")) {
            return "DATE";
        }
        if (normalized.contains("time")) {
            return "DATETIME";
        }
        return "STRING";
    }

    private String inferFieldType(String fieldName, String sourceColumn) {
        String normalized = normalize(fieldName + "_" + sourceColumn);
        if (normalized.endsWith("_id") || normalized.endsWith("id")) {
            return "LONG";
        }
        if (normalized.contains("amount") || normalized.contains("price") || normalized.contains("total")
            || normalized.contains("rate") || normalized.contains("score")) {
            return "DECIMAL";
        }
        if (normalized.startsWith("is_") || normalized.startsWith("has_") || normalized.contains("enabled")
            || normalized.contains("active")) {
            return "BOOLEAN";
        }
        if (normalized.contains("date")) {
            return "DATE";
        }
        if (normalized.contains("time") || normalized.contains("at")) {
            return "DATETIME";
        }
        return "STRING";
    }

    private boolean looksLikeJoinKey(String fieldName, String sourceColumn) {
        String normalized = normalize(fieldName + "_" + sourceColumn);
        return normalized.endsWith("_id") || normalized.endsWith("id");
    }

    private String toCamelCase(String rawName) {
        String sanitized = unquote(rawName).replaceAll("[^A-Za-z0-9_]+", "_");
        String[] parts = sanitized.split("_+");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isBlank()) {
                continue;
            }
            if (builder.isEmpty()) {
                builder.append(part.substring(0, 1).toLowerCase(Locale.ROOT));
                builder.append(part.substring(1));
                continue;
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            builder.append(part.substring(1));
        }
        return builder.isEmpty() ? sanitized : builder.toString();
    }

    private String stripSchemaPrefix(String sourceName) {
        String value = sourceName == null ? "" : sourceName.trim();
        int dotIndex = value.lastIndexOf('.');
        return dotIndex >= 0 ? value.substring(dotIndex + 1) : value;
    }

    private String unquote(String value) {
        String text = value == null ? "" : value.trim();
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("`") && text.endsWith("`"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ConnectionResolutionContext(Map<Long, DSConnection> connectionById,
                                               Map<Long, List<DSCatalog>> catalogsByConnectionId,
                                               DSConnection defaultConnection,
                                               DSCatalog defaultCatalog) {
    }

    private record AliasResolution(Map<String, String> aliasToSourceAlias, Set<String> knownNames) {
    }

    private record SelectedField(String sourceAlias, String sourceColumn, String fieldName, boolean joinKey) {
    }
}
