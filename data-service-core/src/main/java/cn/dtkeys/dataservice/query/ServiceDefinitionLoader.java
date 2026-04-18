package cn.dtkeys.dataservice.query;

import cn.dtkeys.dataservice.common.exception.ServiceConfigInvalidException;
import cn.dtkeys.dataservice.common.exception.ServiceDisabledException;
import cn.dtkeys.dataservice.common.exception.ServiceNotFoundException;
import cn.dtkeys.dataservice.service.model.DSCachePolicy;
import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import cn.dtkeys.dataservice.datasource.model.DSConnection;
import cn.dtkeys.dataservice.service.model.DSDefinition;
import cn.dtkeys.dataservice.service.model.DSField;
import cn.dtkeys.dataservice.service.model.DSParam;
import cn.dtkeys.dataservice.service.model.DSServiceVersion;
import cn.dtkeys.dataservice.service.model.DSSource;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeDefinition;
import cn.dtkeys.dataservice.query.model.DataServiceRuntimeSource;
import cn.dtkeys.dataservice.repository.DSCachePolicyRepository;
import cn.dtkeys.dataservice.repository.DSCatalogRepository;
import cn.dtkeys.dataservice.repository.DSConnectionRepository;
import cn.dtkeys.dataservice.repository.DSDefinitionRepository;
import cn.dtkeys.dataservice.repository.DSFieldRepository;
import cn.dtkeys.dataservice.repository.DSParamRepository;
import cn.dtkeys.dataservice.repository.DSServiceVersionRepository;
import cn.dtkeys.dataservice.repository.DSSourceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按服务编码加载发布态运行时定义。
 */
@Service
@ConditionalOnProperty(name = "data-service.metadata.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceDefinitionLoader {

    private final DSDefinitionRepository dsDefinitionRepository;
    private final DSSourceRepository dsSourceRepository;
    private final DSParamRepository dsParamRepository;
    private final DSFieldRepository dsFieldRepository;
    private final DSCachePolicyRepository dsCachePolicyRepository;
    private final DSServiceVersionRepository dsServiceVersionRepository;
    private final DSConnectionRepository dsConnectionRepository;
    private final DSCatalogRepository dsCatalogRepository;
    private final ObjectMapper objectMapper;

    public ServiceDefinitionLoader(DSDefinitionRepository dsDefinitionRepository,
                                   DSSourceRepository dsSourceRepository,
                                   DSParamRepository dsParamRepository,
                                   DSFieldRepository dsFieldRepository,
                                   DSCachePolicyRepository dsCachePolicyRepository,
                                   DSServiceVersionRepository dsServiceVersionRepository,
                                   DSConnectionRepository dsConnectionRepository,
                                   DSCatalogRepository dsCatalogRepository,
                                   ObjectMapper objectMapper) {
        this.dsDefinitionRepository = dsDefinitionRepository;
        this.dsSourceRepository = dsSourceRepository;
        this.dsParamRepository = dsParamRepository;
        this.dsFieldRepository = dsFieldRepository;
        this.dsCachePolicyRepository = dsCachePolicyRepository;
        this.dsServiceVersionRepository = dsServiceVersionRepository;
        this.dsConnectionRepository = dsConnectionRepository;
        this.dsCatalogRepository = dsCatalogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载指定服务编码的发布态服务定义。
     *
     * @param serviceCode 服务编码
     * @return 运行时服务定义
     */
    public DataServiceRuntimeDefinition loadPublished(String serviceCode) {
        DSDefinition definition = dsDefinitionRepository.findByServiceCode(serviceCode);
        if (definition == null) {
            throw new ServiceNotFoundException("未找到数据服务: " + serviceCode);
        }
        if (!"PUBLISHED".equalsIgnoreCase(definition.getStatus())) {
            throw new ServiceDisabledException("数据服务未发布或不可用: " + serviceCode);
        }

        PublishedRuntimeSnapshot snapshot = resolvePublishedSnapshot(definition);
        definition = snapshot.definition();
        List<DSSource> sources = snapshot.sources();
        List<DSParam> params = snapshot.params();
        List<DSField> fields = snapshot.fields();
        DSCachePolicy cachePolicy = dsCachePolicyRepository.findByServiceId(definition.getId());

        if (sources.isEmpty()) {
            throw new ServiceConfigInvalidException("数据服务未配置查询来源: " + serviceCode);
        }
        if (definition.getSqlTemplate() == null || definition.getSqlTemplate().isBlank()) {
            throw new ServiceConfigInvalidException("数据服务未配置 SQL 模板: " + serviceCode);
        }

        List<DataServiceRuntimeSource> runtimeSources = new ArrayList<>(sources.size());
        for (DSSource source : sources) {
            if (!"ENABLED".equalsIgnoreCase(source.getStatus())) {
                throw new ServiceConfigInvalidException("查询来源未启用, sourceId=" + source.getId());
            }
            DSConnection connection = dsConnectionRepository.findById(source.getConnectionId());
            if (connection == null) {
                throw new ServiceConfigInvalidException("查询来源缺少连接配置, sourceId=" + source.getId());
            }
            if (!"ENABLED".equalsIgnoreCase(connection.getStatus())) {
                throw new ServiceConfigInvalidException("查询来源连接未启用, sourceId=" + source.getId());
            }

            DSCatalog catalog = null;
            if (source.getCatalogId() != null) {
                catalog = dsCatalogRepository.findById(source.getCatalogId());
                if (catalog == null) {
                    throw new ServiceConfigInvalidException("查询来源缺少目录配置, sourceId=" + source.getId());
                }
                if (!"ENABLED".equalsIgnoreCase(catalog.getStatus())) {
                    throw new ServiceConfigInvalidException("查询来源目录未启用, sourceId=" + source.getId());
                }
            }

            runtimeSources.add(new DataServiceRuntimeSource(source, connection, catalog));
        }

        return new DataServiceRuntimeDefinition(definition, List.copyOf(runtimeSources), List.copyOf(params),
            List.copyOf(fields), cachePolicy);
    }

    private PublishedRuntimeSnapshot resolvePublishedSnapshot(DSDefinition currentDefinition) {
        if (currentDefinition.getVersion() == null || currentDefinition.getVersion() <= 0) {
            return loadCurrentMetadata(currentDefinition);
        }
        DSServiceVersion publishedVersion = dsServiceVersionRepository.findByServiceIdAndVersion(
            currentDefinition.getId(),
            currentDefinition.getVersion()
        );
        if (publishedVersion == null) {
            return loadCurrentMetadata(currentDefinition);
        }

        DSDefinition snapshotDefinition = readValue(publishedVersion.getServiceDefinitionJson(), DSDefinition.class);
        snapshotDefinition.setId(currentDefinition.getId());
        snapshotDefinition.setServiceCode(currentDefinition.getServiceCode());
        snapshotDefinition.setStatus(currentDefinition.getStatus());
        snapshotDefinition.setVersion(publishedVersion.getVersion());
        snapshotDefinition.setCurrentSqlVersion(currentDefinition.getCurrentSqlVersion());
        snapshotDefinition.setPublishedAt(currentDefinition.getPublishedAt());
        snapshotDefinition.setPublishedBy(currentDefinition.getPublishedBy());
        applyPublishedSqlSnapshot(snapshotDefinition, publishedVersion);

        return new PublishedRuntimeSnapshot(
            snapshotDefinition,
            readList(publishedVersion.getSourceDefinitionJson(), new TypeReference<List<DSSource>>() {}),
            readList(publishedVersion.getParamDefinitionJson(), new TypeReference<List<DSParam>>() {}),
            readList(publishedVersion.getFieldDefinitionJson(), new TypeReference<List<DSField>>() {})
        );
    }

    private PublishedRuntimeSnapshot loadCurrentMetadata(DSDefinition definition) {
        return new PublishedRuntimeSnapshot(
            definition,
            dsSourceRepository.findByServiceId(definition.getId()),
            dsParamRepository.findByServiceId(definition.getId()),
            dsFieldRepository.findByServiceId(definition.getId())
        );
    }

    private void applyPublishedSqlSnapshot(DSDefinition definition, DSServiceVersion serviceVersion) {
        if (serviceVersion.getSqlDefinitionJson() == null || serviceVersion.getSqlDefinitionJson().isBlank()) {
            return;
        }
        try {
            JsonNode sqlNode = objectMapper.readTree(serviceVersion.getSqlDefinitionJson());
            definition.setSqlTemplate(readText(sqlNode, "sqlTemplate", definition.getSqlTemplate()));
            definition.setSqlType(readText(sqlNode, "sqlType", definition.getSqlType()));
            definition.setExecutionMode(readText(sqlNode, "executionMode", definition.getExecutionMode()));
            definition.setPlanStatus(readText(sqlNode, "planStatus", definition.getPlanStatus()));
        } catch (JsonProcessingException exception) {
            throw new ServiceConfigInvalidException("已发布服务版本快照解析失败");
        }
    }

    private String readText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || valueNode.asText().isBlank()) {
            return defaultValue;
        }
        return valueNode.asText();
    }

    private <T> T readValue(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new ServiceConfigInvalidException("已发布服务版本快照缺失");
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new ServiceConfigInvalidException("已发布服务版本快照解析失败");
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new ServiceConfigInvalidException("已发布服务版本快照解析失败");
        }
    }

    private record PublishedRuntimeSnapshot(DSDefinition definition,
                                            List<DSSource> sources,
                                            List<DSParam> params,
                                            List<DSField> fields) {
    }
}
