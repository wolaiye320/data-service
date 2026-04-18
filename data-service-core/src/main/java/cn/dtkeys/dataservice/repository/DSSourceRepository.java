package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.service.model.DSSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DSSourceRepository {

    @Insert("""
        insert into ds_source (
            service_id, connection_id, catalog_id, source_alias, source_type, source_value, join_key, config_json,
            status, remark, deleted, created_by, updated_by
        ) values (
            #{serviceId}, #{connectionId}, #{catalogId}, #{sourceAlias}, #{sourceType}, #{sourceValue}, #{joinKey},
            #{configJson}, #{status}, #{remark}, #{deleted}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSSource source);

    @Select("""
        select id, service_id, connection_id, catalog_id, source_alias, source_type, source_value, join_key,
               config_json, status, remark, deleted, created_at, created_by, updated_at, updated_by
        from ds_source
        where service_id = #{serviceId} and deleted = false
        order by id
        """)
    @Results(id = "dsSourceResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "connectionId", column = "connection_id"),
        @Result(property = "catalogId", column = "catalog_id"),
        @Result(property = "sourceAlias", column = "source_alias"),
        @Result(property = "sourceType", column = "source_type"),
        @Result(property = "sourceValue", column = "source_value"),
        @Result(property = "joinKey", column = "join_key"),
        @Result(property = "configJson", column = "config_json"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    List<DSSource> findByServiceId(Long serviceId);

    @Update("""
        delete from ds_source
        where service_id = #{serviceId}
        """)
    int deleteByServiceId(Long serviceId);

    @Select("""
        select count(1)
        from ds_source src
        inner join ds_service svc on svc.id = src.service_id
        where src.connection_id = #{connectionId}
          and src.deleted = false
          and svc.deleted = false
          and svc.status = 'PUBLISHED'
        """)
    long countPublishedReferencesByConnectionId(Long connectionId);
}
