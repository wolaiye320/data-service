package cn.dtkeys.dataservice.datasource;

import cn.dtkeys.dataservice.common.context.OperatorContext;
import cn.dtkeys.dataservice.common.exception.ParamInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceNotFoundException;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.datasource.model.DSSourceCapability;
import cn.dtkeys.dataservice.audit.AuditEvent;
import cn.dtkeys.dataservice.audit.AuditLogService;
import cn.dtkeys.dataservice.datasource.DatasourceConnectionManager;
import cn.dtkeys.dataservice.audit.AuditAction;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.repository.DSSourceCapabilityRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 数据源连接管理应用服务。
 */
@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class ConnectionManagementService {

    private static final String MASKED_CONFIG_PLACEHOLDER = "***MASKED***";
    private static final List<String> SUPPORTED_DB_TYPES = List.of("POSTGRESQL", "MYSQL", "ORACLE");

    private final DSConnectionRepository dsConnectionRepository;
    private final DSCatalogRepository dsCatalogRepository;
    private final DSSourceCapabilityRepository dsSourceCapabilityRepository;
    private final DSSourceRepository dsSourceRepository;
    private final DatasourceConnectionManager datasourceConnectionManager;
    private final AuditLogService auditLogService;

    public ConnectionManagementService(DSConnectionRepository dsConnectionRepository,
                                       DSCatalogRepository dsCatalogRepository,
                                       DSSourceCapabilityRepository dsSourceCapabilityRepository,
                                       DSSourceRepository dsSourceRepository,
                                       DatasourceConnectionManager datasourceConnectionManager,
                                       AuditLogService auditLogService) {
        this.dsConnectionRepository = dsConnectionRepository;
        this.dsCatalogRepository = dsCatalogRepository;
        this.dsSourceCapabilityRepository = dsSourceCapabilityRepository;
        this.dsSourceRepository = dsSourceRepository;
        this.datasourceConnectionManager = datasourceConnectionManager;
        this.auditLogService = auditLogService;
    }

    public List<ConnectionView> listConnections() {
        return dsConnectionRepository.findAll().stream()
            .map(this::toMaskedConnectionView)
            .toList();
    }

    public ConnectionDetail getConnection(Long id) {
        DSConnection connection = getConnectionEntity(id);
        return new ConnectionDetail(
            toMaskedConnectionView(connection),
            dsCatalogRepository.findByConnectionId(id),
            dsSourceCapabilityRepository.findByConnectionId(id)
        );
    }

    private DSConnection getConnectionEntity(Long id) {
        DSConnection connection = dsConnectionRepository.findById(id);
        if (connection == null) {
            throw new ServiceNotFoundException("未找到连接: " + id);
        }
        return connection;
    }

    public ConnectionDetail createConnection(DSConnection connection, List<DSCatalog> catalogs) {
        validateConnection(connection, true);
        List<DSCatalog> normalizedCatalogs = normalizeCatalogs(connection.getDbType(), catalogs);
        validateCatalogs(connection.getDbType(), normalizedCatalogs);
        ensureConnectionCodeNotExists(connection.getConnectionCode(), null);
        testConnectionInternal(connection, normalizedCatalogs);

        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        connection.setDeleted(false);
        connection.setCreatedBy(operator);
        connection.setUpdatedBy(operator);
        dsConnectionRepository.insert(connection);
        if (connection.getId() == null) {
            connection.setId(dsConnectionRepository.findByCode(connection.getConnectionCode()).getId());
        }
        replaceCatalogs(connection.getId(), normalizedCatalogs, operator);
        auditLogService.record(AuditEvent.of(
            AuditAction.CREATE_CONNECTION.name(),
            null,
            connection.getId(),
            "CONNECTION",
            connection.getConnectionCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "新增数据源连接",
            buildConnectionAuditDetail(null, connection, normalizedCatalogs)
        ));
        return getConnection(connection.getId());
    }

    public ConnectionDetail updateConnection(Long id, DSConnection connection, List<DSCatalog> catalogs) {
        DSConnection existing = getConnectionEntity(id);
        if (isBlank(connection.getPasswordCiphertext())) {
            connection.setPasswordCiphertext(existing.getPasswordCiphertext());
        }
        if (isMaskedConnectionConfigPlaceholder(connection.getConnectionConfigJson())) {
            connection.setConnectionConfigJson(existing.getConnectionConfigJson());
        }
        validateConnection(connection, false);
        List<DSCatalog> normalizedCatalogs = normalizeCatalogs(connection.getDbType(), catalogs);
        validateCatalogs(connection.getDbType(), normalizedCatalogs);
        ensureConnectionCodeNotExists(connection.getConnectionCode(), id);
        testConnectionInternal(connection, normalizedCatalogs);

        String operator = OperatorContext.getOperator().orElse("SYSTEM");
        connection.setId(id);
        connection.setDeleted(existing.getDeleted());
        connection.setCreatedBy(existing.getCreatedBy());
        connection.setUpdatedBy(operator);
        dsConnectionRepository.update(connection);
        replaceCatalogs(id, normalizedCatalogs, operator);
        auditLogService.record(AuditEvent.of(
            AuditAction.UPDATE_CONNECTION.name(),
            null,
            id,
            "CONNECTION",
            connection.getConnectionCode(),
            operator,
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "更新数据源连接",
            buildConnectionAuditDetail(existing, connection, normalizedCatalogs)
        ));
        return getConnection(id);
    }

    public ConnectionDetail updateConnectionStatus(Long id, String status) {
        DSConnection existing = getConnectionEntity(id);
        String normalizedStatus = normalizeStatus(status);
        if ("DISABLED".equals(normalizedStatus) && isReferencedByPublishedService(id)) {
            throw new ServiceConfigInvalidException("连接已被已发布服务引用，禁止停用");
        }
        existing.setStatus(normalizedStatus);
        existing.setUpdatedBy(OperatorContext.getOperator().orElse("SYSTEM"));
        dsConnectionRepository.update(existing);
        auditLogService.record(AuditEvent.of(
            "ENABLED".equals(normalizedStatus) ? AuditAction.ENABLE_CONNECTION.name() : AuditAction.DISABLE_CONNECTION.name(),
            null,
            id,
            "CONNECTION",
            existing.getConnectionCode(),
            OperatorContext.getOperator().orElse("SYSTEM"),
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "更新连接状态",
            Map.of("status", normalizedStatus)
        ));
        return getConnection(id);
    }

    public void testConnection(Long id) {
        DSConnection connection = getConnectionEntity(id);
        List<DSCatalog> catalogs = dsCatalogRepository.findByConnectionId(id);
        testConnectionInternal(connection, catalogs);
        auditLogService.record(AuditEvent.of(
            AuditAction.TEST_CONNECTION.name(),
            null,
            id,
            "CONNECTION",
            connection.getConnectionCode(),
            OperatorContext.getOperator().orElse("SYSTEM"),
            OperatorContext.getRole().orElse("SYSTEM"),
            "SUCCESS",
            "测试数据源连接",
            Map.of("catalogCount", catalogs.size())
        ));
    }

    private void testConnectionInternal(DSConnection connection, List<DSCatalog> catalogs) {
        List<DSCatalog> effectiveCatalogs = catalogs == null || catalogs.isEmpty() ? List.of() : catalogs;
        if (effectiveCatalogs.isEmpty()) {
            try (Connection ignored = datasourceConnectionManager.createDirectDataSource(connection, null).getConnection()) {
                return;
            } catch (Exception exception) {
                throw new ServiceConfigInvalidException("连接测试失败: " + exception.getMessage());
            }
        }

        for (DSCatalog catalog : effectiveCatalogs) {
            catalog.setConnectionId(connection.getId());
            try (Connection ignored = datasourceConnectionManager.createDirectDataSource(connection, catalog).getConnection()) {
                // no-op
            } catch (Exception exception) {
                throw new ServiceConfigInvalidException("连接测试失败: " + catalog.getCatalogCode() + ", " + exception.getMessage());
            }
        }
    }

    private void replaceCatalogs(Long connectionId, List<DSCatalog> catalogs, String operator) {
        List<DSCatalog> existingCatalogs = dsCatalogRepository.findByConnectionId(connectionId);
        Map<String, DSCatalog> existingByStableKey = new LinkedHashMap<>();
        for (DSCatalog existing : existingCatalogs) {
            existingByStableKey.put(buildCatalogStableKey(existing), existing);
        }

        Set<Long> retainedIds = new LinkedHashSet<>();
        for (DSCatalog catalog : catalogs) {
            catalog.setConnectionId(connectionId);
            catalog.setDeleted(false);
            catalog.setUpdatedBy(operator);
            DSCatalog existing = existingByStableKey.get(buildCatalogStableKey(catalog));
            if (existing != null) {
                catalog.setId(existing.getId());
                catalog.setCreatedBy(existing.getCreatedBy());
                dsCatalogRepository.update(catalog);
                retainedIds.add(existing.getId());
                continue;
            }
            catalog.setCreatedBy(operator);
            dsCatalogRepository.insert(catalog);
            retainedIds.add(catalog.getId());
        }

        for (DSCatalog existing : existingCatalogs) {
            if (retainedIds.contains(existing.getId())) {
                continue;
            }
            if (dsSourceRepository.countReferencesByCatalogId(existing.getId()) > 0) {
                throw new ServiceConfigInvalidException("目标库已被服务引用，禁止删除: " + existing.getCatalogName());
            }
            dsCatalogRepository.markDeleted(existing.getId(), operator);
        }
    }

    private void ensureConnectionCodeNotExists(String connectionCode, Long currentId) {
        DSConnection existing = dsConnectionRepository.findByCode(connectionCode);
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new ParamInvalidException("connectionCode 已存在: " + connectionCode);
        }
    }

    private boolean isReferencedByPublishedService(Long connectionId) {
        return dsSourceRepository.countPublishedReferencesByConnectionId(connectionId) > 0;
    }

    private void validateConnection(DSConnection connection, boolean requireCode) {
        if (connection == null) {
            throw new ParamInvalidException("连接配置不能为空");
        }
        if (requireCode && isBlank(connection.getConnectionCode())) {
            throw new ParamInvalidException("connectionCode 不能为空");
        }
        if (isBlank(connection.getConnectionName())) {
            throw new ParamInvalidException("connectionName 不能为空");
        }
        if (isBlank(connection.getDbType())) {
            throw new ParamInvalidException("dbType 不能为空");
        }
        String normalizedDbType = connection.getDbType().trim().toUpperCase();
        if (!SUPPORTED_DB_TYPES.contains(normalizedDbType)) {
            throw new ParamInvalidException("暂不支持的 dbType: " + connection.getDbType());
        }
        connection.setDbType(normalizedDbType);
        if (isBlank(connection.getHost())) {
            throw new ParamInvalidException("host 不能为空");
        }
        if (connection.getPort() == null || connection.getPort() <= 0) {
            throw new ParamInvalidException("port 必须大于 0");
        }
        if (isBlank(connection.getUsername())) {
            throw new ParamInvalidException("username 不能为空");
        }
        if (isBlank(connection.getPasswordCiphertext())) {
            throw new ParamInvalidException("passwordCiphertext 不能为空");
        }
        connection.setStatus(normalizeStatus(connection.getStatus()));
    }

    private List<DSCatalog> normalizeCatalogs(String dbType, List<DSCatalog> catalogs) {
        if (catalogs == null || catalogs.isEmpty()) {
            return List.of();
        }
        List<DSCatalog> normalized = new ArrayList<>(catalogs.size());
        String normalizedDbType = dbType == null ? null : dbType.trim().toUpperCase(Locale.ROOT);
        for (DSCatalog catalog : catalogs) {
            if (catalog == null) {
                continue;
            }
            DSCatalog next = new DSCatalog();
            next.setId(catalog.getId());
            next.setConnectionId(catalog.getConnectionId());
            next.setCatalogValue(trimToNull(catalog.getCatalogValue()));
            next.setCatalogType(resolveCatalogType(normalizedDbType, catalog.getCatalogType()));
            String normalizedValue = next.getCatalogValue();
            next.setCatalogCode(trimToNull(catalog.getCatalogCode()));
            if (next.getCatalogCode() == null && normalizedValue != null) {
                next.setCatalogCode(generateCatalogCode(normalizedValue));
            }
            next.setCatalogName(trimToNull(catalog.getCatalogName()));
            if (next.getCatalogName() == null) {
                next.setCatalogName(normalizedValue);
            }
            next.setStatus(normalizeStatus(catalog.getStatus()));
            next.setRemark(trimToNull(catalog.getRemark()));
            next.setDeleted(catalog.getDeleted());
            next.setCreatedAt(catalog.getCreatedAt());
            next.setCreatedBy(catalog.getCreatedBy());
            next.setUpdatedAt(catalog.getUpdatedAt());
            next.setUpdatedBy(catalog.getUpdatedBy());
            normalized.add(next);
        }
        return List.copyOf(normalized);
    }

    private void validateCatalogs(String dbType, List<DSCatalog> catalogs) {
        if (catalogs == null) {
            return;
        }
        Set<String> stableKeys = new LinkedHashSet<>();
        for (DSCatalog catalog : catalogs) {
            if (isBlank(catalog.getCatalogCode())) {
                throw new ParamInvalidException("catalogCode 不能为空");
            }
            if (isBlank(catalog.getCatalogName())) {
                throw new ParamInvalidException("catalogName 不能为空");
            }
            if (isBlank(catalog.getCatalogType())) {
                throw new ParamInvalidException("catalogType 不能为空");
            }
            if (isBlank(catalog.getCatalogValue())) {
                throw new ParamInvalidException("catalogValue 不能为空");
            }
            ensureCatalogTypeAllowed(dbType, catalog.getCatalogType());
            catalog.setStatus(normalizeStatus(catalog.getStatus()));
            String stableKey = buildCatalogStableKey(catalog);
            if (!stableKeys.add(stableKey)) {
                throw new ParamInvalidException("目标库重复: " + catalog.getCatalogValue());
            }
        }
    }

    private void ensureCatalogTypeAllowed(String dbType, String catalogType) {
        String normalizedDbType = dbType == null ? null : dbType.trim().toUpperCase(Locale.ROOT);
        String normalizedCatalogType = catalogType.trim().toUpperCase(Locale.ROOT);
        if (Objects.equals(normalizedDbType, "POSTGRESQL") && !"SCHEMA".equals(normalizedCatalogType)) {
            throw new ParamInvalidException("PostgreSQL 目标库类型仅支持 SCHEMA");
        }
        if (Objects.equals(normalizedDbType, "MYSQL") && !"DATABASE".equals(normalizedCatalogType)) {
            throw new ParamInvalidException("MySQL 目标库类型仅支持 DATABASE");
        }
        if (Objects.equals(normalizedDbType, "ORACLE")
            && !"DATABASE".equals(normalizedCatalogType)
            && !"SCHEMA".equals(normalizedCatalogType)) {
            throw new ParamInvalidException("Oracle 目标库类型仅支持 DATABASE 或 SCHEMA");
        }
    }

    private String resolveCatalogType(String dbType, String catalogType) {
        if (!isBlank(catalogType)) {
            return catalogType.trim().toUpperCase(Locale.ROOT);
        }
        if (dbType == null) {
            return null;
        }
        return switch (dbType) {
            case "POSTGRESQL" -> "SCHEMA";
            case "MYSQL", "ORACLE" -> "DATABASE";
            default -> null;
        };
    }

    private String generateCatalogCode(String catalogValue) {
        String normalized = catalogValue.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "catalog" : normalized;
    }

    private String buildCatalogStableKey(DSCatalog catalog) {
        return catalog.getCatalogType().trim().toUpperCase(Locale.ROOT) + ":" + catalog.getCatalogValue().trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            return "ENABLED";
        }
        String normalized = status.trim().toUpperCase();
        if (!"ENABLED".equals(normalized) && !"DISABLED".equals(normalized) && !"DRAFT".equals(normalized)
            && !"PUBLISHED".equals(normalized)) {
            throw new ParamInvalidException("非法状态值: " + status);
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isMaskedConnectionConfigPlaceholder(String value) {
        return MASKED_CONFIG_PLACEHOLDER.equals(value);
    }

    private ConnectionView toMaskedConnectionView(DSConnection connection) {
        return ConnectionView.from(connection, true);
    }

    private Map<String, Object> buildConnectionAuditDetail(DSConnection before,
                                                           DSConnection after,
                                                           List<DSCatalog> catalogs) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        if (before != null) {
            detail.put("before", summarizeConnection(before));
        }
        detail.put("after", summarizeConnection(after));
        detail.put("catalogCount", catalogs.size());
        return detail;
    }

    private Map<String, Object> summarizeConnection(DSConnection connection) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("connectionCode", connection.getConnectionCode());
        summary.put("connectionName", connection.getConnectionName());
        summary.put("dbType", connection.getDbType());
        summary.put("host", connection.getHost());
        summary.put("port", connection.getPort());
        summary.put("username", connection.getUsername());
        summary.put("passwordCiphertext", ConnectionView.from(connection, true).passwordCiphertext());
        summary.put("status", connection.getStatus());
        summary.put("connectionConfigJson", ConnectionView.from(connection, true).connectionConfigJson());
        return summary;
    }

    public record ConnectionView(Long id,
                                 String connectionCode,
                                 String connectionName,
                                 String dbType,
                                 String host,
                                 Integer port,
                                 String username,
                                 String passwordCiphertext,
                                 String status,
                                 String remark,
                                 String connectionConfigJson,
                                 Boolean deleted,
                                 LocalDateTime createdAt,
                                 String createdBy,
                                 LocalDateTime updatedAt,
                                 String updatedBy) {

        public static ConnectionView from(DSConnection connection, boolean masked) {
            return new ConnectionView(
                connection.getId(),
                connection.getConnectionCode(),
                connection.getConnectionName(),
                connection.getDbType(),
                connection.getHost(),
                connection.getPort(),
                connection.getUsername(),
                masked ? maskSecret(connection.getPasswordCiphertext()) : connection.getPasswordCiphertext(),
                connection.getStatus(),
                connection.getRemark(),
                masked ? maskJson(connection.getConnectionConfigJson()) : connection.getConnectionConfigJson(),
                connection.getDeleted(),
                connection.getCreatedAt(),
                connection.getCreatedBy(),
                connection.getUpdatedAt(),
                connection.getUpdatedBy()
            );
        }

        private static String maskSecret(String value) {
            if (value == null || value.isBlank()) {
                return value;
            }
            if (value.length() <= 4) {
                return "****";
            }
            return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        }

        private static String maskJson(String value) {
            if (value == null || value.isBlank()) {
                return value;
            }
            return "***MASKED***";
        }
    }

    public record ConnectionDetail(ConnectionView connection,
                                   List<DSCatalog> catalogs,
                                   List<DSSourceCapability> capabilities) {
    }
}
