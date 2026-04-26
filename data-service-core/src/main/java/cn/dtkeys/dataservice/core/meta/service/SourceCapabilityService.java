package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import cn.dtkeys.dataservice.core.meta.domain.DsSourceCapabilityRecord;
import cn.dtkeys.dataservice.core.meta.repository.DsSourceCapabilityRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 统一封装数据源能力的加载与作用域归一化逻辑。
 */
@Service
public class SourceCapabilityService {

    private static final Comparator<CapabilityDescriptor> CAPABILITY_ORDER =
            Comparator.comparing(CapabilityDescriptor::capabilityCode)
                    .thenComparing(CapabilityDescriptor::ownerLevel)
                    .thenComparing(CapabilityDescriptor::scopeType)
                    .thenComparing(item -> Objects.toString(item.scopeValue(), ""))
                    .thenComparing(CapabilityDescriptor::id);

    private final DsSourceCapabilityRepository sourceCapabilityRepository;

    public SourceCapabilityService(DsSourceCapabilityRepository sourceCapabilityRepository) {
        this.sourceCapabilityRepository = sourceCapabilityRepository;
    }

    /**
     * 按连接加载当前生效的数据源能力，并按稳定顺序输出。
     */
    public List<CapabilityDescriptor> loadCapabilities(DsConnectionRecord connection) {
        if (connection == null || connection.getId() == null) {
            return List.of();
        }
        return sourceCapabilityRepository.findEnabledByConnectionId(connection.getId()).stream()
                .map(record -> toDescriptor(connection, record))
                .sorted(CAPABILITY_ORDER)
                .toList();
    }

    /**
     * 判断连接是否已配置任一启用中的数据源能力。
     */
    public boolean hasEnabledCapabilities(DsConnectionRecord connection) {
        return !loadCapabilities(connection).isEmpty();
    }

    /**
     * 为发布计划、诊断和后续规划链路生成精简能力摘要。
     */
    public CapabilitySummary summarize(DsConnectionRecord connection) {
        List<CapabilityDescriptor> capabilities = loadCapabilities(connection);
        return new CapabilitySummary(
                connection == null ? null : connection.getConnectionCode(),
                connection == null ? null : connection.getDbType(),
                resolveOwnerLevel(connection),
                capabilities
        );
    }

    private CapabilityDescriptor toDescriptor(DsConnectionRecord connection, DsSourceCapabilityRecord record) {
        return new CapabilityDescriptor(
                record.getId(),
                connection.getConnectionCode(),
                record.getDbType(),
                resolveOwnerLevel(connection),
                SourceCapabilityScopeType.fromDatabaseValue(record.getScope()),
                record.getScopeValue(),
                record.getCapabilityCode(),
                record.getCapabilityValue(),
                record.getCapabilityDetailJson()
        );
    }

    private SourceCapabilityOwnerLevel resolveOwnerLevel(DsConnectionRecord connection) {
        if (connection != null && connection.getId() != null) {
            return SourceCapabilityOwnerLevel.CONNECTION;
        }
        return SourceCapabilityOwnerLevel.GLOBAL;
    }

    public record CapabilityDescriptor(
            Long id,
            String connectionCode,
            String dbType,
            SourceCapabilityOwnerLevel ownerLevel,
            SourceCapabilityScopeType scopeType,
            String scopeValue,
            String capabilityCode,
            String capabilityValue,
            String capabilityDetailJson
    ) {
    }

    public record CapabilitySummary(
            String connectionCode,
            String dbType,
            SourceCapabilityOwnerLevel ownerLevel,
            List<CapabilityDescriptor> capabilities
    ) {
        public int capabilityCount() {
            return capabilities.size();
        }
    }
}
