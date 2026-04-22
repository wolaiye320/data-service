package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.service.model.DSParam;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DSParamRepository {

    @Insert("""
        insert into ds_param (
            service_id, param_name, display_name, param_type, sql_placeholder, required, default_value,
            sort_order, deleted, created_by, updated_by
        ) values (
            #{serviceId}, #{paramName}, #{displayName}, #{paramType}, #{sqlPlaceholder}, #{required},
            #{defaultValue}, #{sortOrder}, #{deleted}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSParam param);

    @Select("""
        select id, service_id, param_name, display_name, param_type, sql_placeholder, required, default_value,
               sort_order, deleted, created_at, created_by, updated_at, updated_by
        from ds_param
        where service_id = #{serviceId} and deleted = false
        order by sort_order, id
        """)
    @Results(id = "dsParamResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "paramName", column = "param_name"),
        @Result(property = "displayName", column = "display_name"),
        @Result(property = "paramType", column = "param_type"),
        @Result(property = "sqlPlaceholder", column = "sql_placeholder"),
        @Result(property = "defaultValue", column = "default_value"),
        @Result(property = "sortOrder", column = "sort_order"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    List<DSParam> findByServiceId(Long serviceId);

    @Update("""
        delete from ds_param
        where service_id = #{serviceId}
        """)
    int deleteByServiceId(Long serviceId);
}
