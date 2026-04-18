package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.datasource.model.DSSourceCapability;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DSSourceCapabilityRepository {

    @Insert("""
        insert into ds_source_capability (
            connection_id, db_type, capability_code, capability_value, capability_detail_json,
            scope, scope_value, enabled, remark
        ) values (
            #{connectionId}, #{dbType}, #{capabilityCode}, #{capabilityValue}, #{capabilityDetailJson},
            #{scope}, #{scopeValue}, #{enabled}, #{remark}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSSourceCapability capability);

    @Delete("""
        delete from ds_source_capability
        where connection_id = #{connectionId}
        """)
    int deleteByConnectionId(Long connectionId);

    @Select("""
        select id, connection_id, db_type, capability_code, capability_value, capability_detail_json,
               scope, scope_value, enabled, remark, created_at, updated_at
        from ds_source_capability
        where connection_id = #{connectionId}
        order by id
        """)
    @Results(id = "dsSourceCapabilityResult", value = {
        @Result(property = "connectionId", column = "connection_id"),
        @Result(property = "dbType", column = "db_type"),
        @Result(property = "capabilityCode", column = "capability_code"),
        @Result(property = "capabilityValue", column = "capability_value"),
        @Result(property = "capabilityDetailJson", column = "capability_detail_json"),
        @Result(property = "scopeValue", column = "scope_value"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    List<DSSourceCapability> findByConnectionId(Long connectionId);
}
