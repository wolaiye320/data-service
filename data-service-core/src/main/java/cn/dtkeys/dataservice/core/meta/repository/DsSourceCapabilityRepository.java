package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsSourceCapabilityRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DsSourceCapabilityRepository {

    /**
     * 新增数据源能力记录。
     */
    @Insert("""
            insert into ds_source_capability (
                connection_id, db_type, capability_code, capability_value, capability_detail_json,
                scope, scope_value, status, remark
            ) values (
                #{record.connectionId}, #{record.dbType}, #{record.capabilityCode}, #{record.capabilityValue}, #{record.capabilityDetailJson},
                #{record.scope}, #{record.scopeValue}, #{record.status}, #{record.remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsSourceCapabilityRecord record);

    /**
     * 删除连接下全部能力定义。
     */
    @Delete("""
            delete from ds_source_capability
            where connection_id = #{connectionId}
            """)
    int deleteByConnectionId(@Param("connectionId") Long connectionId);

    /**
     * 查询连接下启用中的能力清单。
     */
    @Select("""
            select *
            from ds_source_capability
            where connection_id = #{connectionId}
              and status = 'ENABLED'
            order by capability_code asc, created_at asc, id asc
            """)
    List<DsSourceCapabilityRecord> findEnabledByConnectionId(@Param("connectionId") Long connectionId);
}
