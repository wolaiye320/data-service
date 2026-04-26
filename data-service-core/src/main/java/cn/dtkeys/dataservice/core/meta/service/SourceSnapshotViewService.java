package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import cn.dtkeys.dataservice.core.meta.web.response.SourceSnapshotItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SourceSnapshotViewService {

    private static final TypeReference<List<SqlSourceSnapshotService.SourceSnapshot>> SOURCE_SNAPSHOT_LIST =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public SourceSnapshotViewService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将来源快照 JSON 组装为前端可直接展示的结构化数据。
     */
    public List<SourceSnapshotItemResponse> toItems(String sourceSnapshotJson) {
        if (sourceSnapshotJson == null || sourceSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            List<SqlSourceSnapshotService.SourceSnapshot> snapshots =
                    objectMapper.readValue(sourceSnapshotJson, SOURCE_SNAPSHOT_LIST);
            return snapshots.stream()
                    .map(snapshot -> new SourceSnapshotItemResponse(
                            snapshot.connectionCode(),
                            snapshot.schemaName(),
                            snapshot.databaseName(),
                            snapshot.tableName(),
                            snapshot.alias(),
                            snapshot.dbType()
                    ))
                    .toList();
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "来源快照反序列化失败", ex);
        }
    }
}
