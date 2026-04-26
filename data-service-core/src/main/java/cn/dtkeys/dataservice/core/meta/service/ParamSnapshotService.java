package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.web.request.ParamDefinitionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParamSnapshotService {

    private static final TypeReference<List<DomaSqlTemplateParser.ParamSnapshot>> PARAM_SNAPSHOT_LIST =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public ParamSnapshotService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 合并用户确认的参数类型，并校验请求参数与 SQL 快照一致。
     */
    public String mergeConfirmedTypes(DomaSqlTemplateParser.ParseResult parseResult,
                                      List<ParamDefinitionRequest> paramDefinitions) {
        return toJson(applyParamDefinitions(parseResult.params(), paramDefinitions));
    }

    /**
     * 校验快照中的参数类型均已确认，供发布兜底复用。
     */
    public void validateConfirmed(String paramSnapshotJson) {
        List<DomaSqlTemplateParser.ParamSnapshot> snapshots = parseSnapshotJson(paramSnapshotJson);
        for (DomaSqlTemplateParser.ParamSnapshot snapshot : snapshots) {
            if (isUnknown(snapshot.paramType())) {
                throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 参数类型未确认: " + snapshot.paramName());
            }
        }
    }

    /**
     * 基于当前 SQL 和已确认的参数类型，重新生成参数快照。
     */
    public String rebuildConfirmedSnapshot(String sqlText, String existingParamSnapshotJson) {
        List<DomaSqlTemplateParser.ParamSnapshot> existingSnapshots = parseSnapshotJson(existingParamSnapshotJson);
        List<ParamDefinitionRequest> paramDefinitions = existingSnapshots.stream()
                .map(snapshot -> new ParamDefinitionRequest(snapshot.paramName(), snapshot.paramType()))
                .toList();
        return mergeConfirmedTypes(new DomaSqlTemplateParser(objectMapper).parse(sqlText), paramDefinitions);
    }

    private List<DomaSqlTemplateParser.ParamSnapshot> applyParamDefinitions(
            List<DomaSqlTemplateParser.ParamSnapshot> parsedParams,
            List<ParamDefinitionRequest> paramDefinitions
    ) {
        if (parsedParams.isEmpty()) {
            if (paramDefinitions != null && !paramDefinitions.isEmpty()) {
                throw new DataServiceException(
                        ErrorCode.INVALID_ARGUMENT,
                        "参数类型确认包含未使用的 SQL 参数: " + paramDefinitions.getFirst().paramName()
                );
            }
            return List.of();
        }

        Map<String, ParamDefinitionRequest> definitionsByName = new LinkedHashMap<>();
        if (paramDefinitions != null) {
            for (ParamDefinitionRequest paramDefinition : paramDefinitions) {
                definitionsByName.put(paramDefinition.paramName(), paramDefinition);
            }
        }

        List<DomaSqlTemplateParser.ParamSnapshot> merged = parsedParams.stream()
                .map(snapshot -> mergeSnapshot(snapshot, definitionsByName.remove(snapshot.paramName())))
                .toList();

        if (!definitionsByName.isEmpty()) {
            String redundantParam = definitionsByName.keySet().iterator().next();
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "参数类型确认包含未使用的 SQL 参数: " + redundantParam);
        }
        return merged;
    }

    private DomaSqlTemplateParser.ParamSnapshot mergeSnapshot(DomaSqlTemplateParser.ParamSnapshot snapshot,
                                                              ParamDefinitionRequest paramDefinition) {
        if (paramDefinition == null || isUnknown(paramDefinition.paramType())) {
            throw new DataServiceException(ErrorCode.INVALID_ARGUMENT, "SQL 参数类型未确认: " + snapshot.paramName());
        }
        return new DomaSqlTemplateParser.ParamSnapshot(
                snapshot.paramName(),
                paramDefinition.paramType(),
                snapshot.placeholder(),
                snapshot.collection(),
                snapshot.defaultValue()
        );
    }

    private List<DomaSqlTemplateParser.ParamSnapshot> parseSnapshotJson(String paramSnapshotJson) {
        if (paramSnapshotJson == null || paramSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(paramSnapshotJson, PARAM_SNAPSHOT_LIST);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "参数快照反序列化失败", ex);
        }
    }

    private String toJson(List<DomaSqlTemplateParser.ParamSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "参数快照序列化失败", ex);
        }
    }

    private boolean isUnknown(String paramType) {
        return paramType == null || paramType.isBlank() || "UNKNOWN".equalsIgnoreCase(paramType);
    }
}
