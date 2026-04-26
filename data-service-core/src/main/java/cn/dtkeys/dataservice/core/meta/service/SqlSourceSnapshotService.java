package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.error.ResourceNotFoundException;
import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsConnectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlSourceSnapshotService {

    private static final String SQL_KEYWORD_LOOKAHEAD =
            "(?!(?:where|join|left|right|inner|outer|full|on|group|order|having|limit|offset|union|cross)\\b)";
    private static final Pattern SIMPLE_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)(?:\\s+(?:as\\s+)?" +
                    SQL_KEYWORD_LOOKAHEAD + "([a-zA-Z0-9_]+))?"
    );
    private static final Pattern FEDERATED_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([a-zA-Z0-9_]+)@([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)(?:\\s+(?:as\\s+)?" +
                    SQL_KEYWORD_LOOKAHEAD + "([a-zA-Z0-9_]+))?"
    );
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "where", "join", "left", "right", "inner", "outer", "full", "on", "group", "order", "having",
            "limit", "offset", "union", "cross"
    );

    private final DsConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    public SqlSourceSnapshotService(DsConnectionRepository connectionRepository, ObjectMapper objectMapper) {
        this.connectionRepository = connectionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成来源快照 JSON。
     */
    public String buildSourceSnapshotJson(String sqlType, String sqlText, String defaultConnectionCode) {
        List<SourceSnapshot> snapshots = buildSnapshots(sqlType, sqlText, defaultConnectionCode);
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "来源快照序列化失败", ex);
        }
    }

    /**
     * 校验联邦 SQL 的多表来源必须显式别名。
     */
    public void validateFederatedAliases(String sqlType, String sqlText) {
        if (!"FEDERATED_SQL".equals(sqlType)) {
            return;
        }
        Matcher matcher = FEDERATED_SQL_PATTERN.matcher(sqlText);
        int refCount = 0;
        while (matcher.find()) {
            refCount++;
            String alias = normalizeAlias(matcher.group(4));
            if (refCount > 1 && (alias == null || alias.isBlank())) {
                throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "联邦 SQL 多表查询必须显式指定表别名");
            }
        }
    }

    private List<SourceSnapshot> buildSnapshots(String sqlType, String sqlText, String defaultConnectionCode) {
        if ("SIMPLE_SQL".equals(sqlType)) {
            return buildSimpleSqlSnapshots(sqlText, defaultConnectionCode);
        }
        if ("FEDERATED_SQL".equals(sqlType)) {
            return buildFederatedSqlSnapshots(sqlText);
        }
        return List.of();
    }

    private List<SourceSnapshot> buildSimpleSqlSnapshots(String sqlText, String defaultConnectionCode) {
        DsConnectionRecord connection = connectionRepository.findByConnectionCode(defaultConnectionCode);
        if (connection == null) {
            throw new ResourceNotFoundException("默认连接不存在: " + defaultConnectionCode);
        }

        String databaseName = extractDatabaseName(connection.getConnectionConfigJson());
        Map<String, SourceSnapshot> ordered = new LinkedHashMap<>();
        Matcher matcher = SIMPLE_SQL_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            String schemaOrDatabase = matcher.group(1);
            String tableName = matcher.group(2);
            String alias = normalizeAlias(matcher.group(3));
            SourceSnapshot snapshot = buildSnapshot(connection, databaseName, schemaOrDatabase, tableName, alias);
            ordered.putIfAbsent(snapshot.uniqueKey(), snapshot);
        }
        return new ArrayList<>(ordered.values());
    }

    private List<SourceSnapshot> buildFederatedSqlSnapshots(String sqlText) {
        Map<String, SourceSnapshot> ordered = new LinkedHashMap<>();
        Matcher matcher = FEDERATED_SQL_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            String connectionCode = matcher.group(1);
            String schemaOrDatabase = matcher.group(2);
            String tableName = matcher.group(3);
            String alias = normalizeAlias(matcher.group(4));
            DsConnectionRecord connection = connectionRepository.findByConnectionCode(connectionCode);
            if (connection == null) {
                throw new ResourceNotFoundException("SQL 引用的连接不存在: connection=" + connectionCode);
            }
            String databaseName = extractDatabaseName(connection.getConnectionConfigJson());
            SourceSnapshot snapshot = buildSnapshot(connection, databaseName, schemaOrDatabase, tableName, alias);
            ordered.putIfAbsent(snapshot.uniqueKey(), snapshot);
        }
        return new ArrayList<>(ordered.values());
    }

    private SourceSnapshot buildSnapshot(DsConnectionRecord connection,
                                         String databaseName,
                                         String schemaOrDatabase,
                                         String tableName,
                                         String alias) {
        String dbType = connection.getDbType();
        String schemaName = schemaOrDatabase;
        String effectiveDatabaseName = databaseName;
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            schemaName = null;
            effectiveDatabaseName = schemaOrDatabase;
        }
        return new SourceSnapshot(
                connection.getConnectionCode(),
                schemaName,
                effectiveDatabaseName,
                tableName,
                alias,
                dbType
        );
    }

    private String normalizeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return null;
        }
        String normalized = alias.toLowerCase(Locale.ROOT);
        return SQL_KEYWORDS.contains(normalized) ? null : alias;
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

    public record SourceSnapshot(String connectionCode,
                                 String schemaName,
                                 String databaseName,
                                 String tableName,
                                 String alias,
                                 String dbType) {

        private String uniqueKey() {
            return String.join("|",
                    connectionCode == null ? "" : connectionCode,
                    schemaName == null ? "" : schemaName,
                    databaseName == null ? "" : databaseName,
                    tableName == null ? "" : tableName,
                    alias == null ? "" : alias,
                    dbType == null ? "" : dbType
            );
        }
    }
}
