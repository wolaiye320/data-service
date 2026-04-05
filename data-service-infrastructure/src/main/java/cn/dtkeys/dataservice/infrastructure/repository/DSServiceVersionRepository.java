package cn.dtkeys.dataservice.infrastructure.repository;

import cn.dtkeys.dataservice.domain.model.DSServiceVersion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DSServiceVersionRepository {

    @Insert("""
        insert into ds_service_version (
            service_id, version, status, service_definition_json, source_definition_json,
            param_definition_json, field_definition_json, sql_definition_json, created_by
        ) values (
            #{serviceId}, #{version}, #{status}, #{serviceDefinitionJson}, #{sourceDefinitionJson},
            #{paramDefinitionJson}, #{fieldDefinitionJson}, #{sqlDefinitionJson}, #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSServiceVersion serviceVersion);

    @Select("""
        select id, service_id, version, status, service_definition_json, source_definition_json,
               param_definition_json, field_definition_json, sql_definition_json, created_by, created_at
        from ds_service_version
        where service_id = #{serviceId}
        order by version desc, id desc
        """)
    @Results(id = "dsServiceVersionResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "serviceDefinitionJson", column = "service_definition_json"),
        @Result(property = "sourceDefinitionJson", column = "source_definition_json"),
        @Result(property = "paramDefinitionJson", column = "param_definition_json"),
        @Result(property = "fieldDefinitionJson", column = "field_definition_json"),
        @Result(property = "sqlDefinitionJson", column = "sql_definition_json"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<DSServiceVersion> findByServiceId(Long serviceId);

    @Select("""
        select id, service_id, version, status, service_definition_json, source_definition_json,
               param_definition_json, field_definition_json, sql_definition_json, created_by, created_at
        from ds_service_version
        where service_id = #{serviceId}
          and version = #{version}
        limit 1
        """)
    @Results(id = "dsServiceVersionByVersionResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "serviceDefinitionJson", column = "service_definition_json"),
        @Result(property = "sourceDefinitionJson", column = "source_definition_json"),
        @Result(property = "paramDefinitionJson", column = "param_definition_json"),
        @Result(property = "fieldDefinitionJson", column = "field_definition_json"),
        @Result(property = "sqlDefinitionJson", column = "sql_definition_json"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "createdAt", column = "created_at")
    })
    DSServiceVersion findByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);
}
