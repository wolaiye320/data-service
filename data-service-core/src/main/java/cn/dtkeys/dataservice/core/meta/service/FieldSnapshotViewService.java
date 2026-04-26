package cn.dtkeys.dataservice.core.meta.service;

import cn.dtkeys.dataservice.core.error.DataServiceException;
import cn.dtkeys.dataservice.core.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FieldSnapshotViewService {

    private static final TypeReference<List<SqlFieldSnapshotService.FieldSnapshot>> FIELD_SNAPSHOT_LIST =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;

    public FieldSnapshotViewService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将字段快照 JSON 反序列化为结构化字段列表，供正式查询结果映射复用。
     */
    public List<SqlFieldSnapshotService.FieldSnapshot> toItems(String fieldSnapshotJson) {
        if (fieldSnapshotJson == null || fieldSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(fieldSnapshotJson, FIELD_SNAPSHOT_LIST);
        } catch (JsonProcessingException ex) {
            throw new DataServiceException(ErrorCode.INTERNAL_ERROR, "字段快照反序列化失败", ex);
        }
    }
}
