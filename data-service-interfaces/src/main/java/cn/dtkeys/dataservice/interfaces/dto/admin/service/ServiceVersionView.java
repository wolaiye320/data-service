package cn.dtkeys.dataservice.interfaces.dto.admin.service;

import cn.dtkeys.dataservice.domain.model.DSServiceVersion;

import java.time.LocalDateTime;

public record ServiceVersionView(Long id,
                                 Long serviceId,
                                 Integer version,
                                 String status,
                                 String serviceDefinitionJson,
                                 String sourceDefinitionJson,
                                 String paramDefinitionJson,
                                 String fieldDefinitionJson,
                                 String sqlDefinitionJson,
                                 String createdBy,
                                 LocalDateTime createdAt) {

    public static ServiceVersionView from(DSServiceVersion version) {
        return new ServiceVersionView(
            version.getId(),
            version.getServiceId(),
            version.getVersion(),
            version.getStatus(),
            version.getServiceDefinitionJson(),
            version.getSourceDefinitionJson(),
            version.getParamDefinitionJson(),
            version.getFieldDefinitionJson(),
            version.getSqlDefinitionJson(),
            version.getCreatedBy(),
            version.getCreatedAt()
        );
    }
}
