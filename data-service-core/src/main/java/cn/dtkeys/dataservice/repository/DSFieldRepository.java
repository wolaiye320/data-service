package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.service.model.DSField;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DSFieldRepository {

    @Insert("""
        insert into ds_field (
            service_id, source_alias, source_column, field_name, display_name, field_type, sort_order,
            primary_key, join_key, deleted, created_by, updated_by
        ) values (
            #{serviceId}, #{sourceAlias}, #{sourceColumn}, #{fieldName}, #{displayName}, #{fieldType}, #{sortOrder},
            #{primaryKey}, #{joinKey}, #{deleted}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSField field);

    @Select("""
        select id, service_id, source_alias, source_column, field_name, display_name, field_type, sort_order,
               primary_key, join_key, deleted, created_at, created_by, updated_at, updated_by
        from ds_field
        where service_id = #{serviceId} and deleted = false
        order by sort_order, id
        """)
    @Results(id = "dsFieldResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "sourceAlias", column = "source_alias"),
        @Result(property = "sourceColumn", column = "source_column"),
        @Result(property = "fieldName", column = "field_name"),
        @Result(property = "displayName", column = "display_name"),
        @Result(property = "fieldType", column = "field_type"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "primaryKey", column = "primary_key"),
        @Result(property = "joinKey", column = "join_key"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    List<DSField> findByServiceId(Long serviceId);

    @Update("""
        delete from ds_field
        where service_id = #{serviceId}
        """)
    int deleteByServiceId(Long serviceId);
}
