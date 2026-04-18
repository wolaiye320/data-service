package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.service.model.DSSqlValidateLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DSSqlValidateLogRepository {

    @Insert("""
        insert into ds_sql_validate_log (
            service_id, version, validate_stage, result, message, detail_json, trace_id, created_by
        ) values (
            #{serviceId}, #{version}, #{validateStage}, #{result}, #{message}, #{detailJson}, #{traceId}, #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSSqlValidateLog validateLog);

    @Delete("""
        delete from ds_sql_validate_log
        where service_id = #{serviceId} and version = #{version}
        """)
    int deleteByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);

    @Select("""
        select id, service_id, version, validate_stage, result, message, detail_json, trace_id, created_at, created_by
        from ds_sql_validate_log
        where service_id = #{serviceId} and version = #{version}
        order by id
        """)
    @Results(id = "dsSqlValidateLogResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "validateStage", column = "validate_stage"),
        @Result(property = "detailJson", column = "detail_json"),
        @Result(property = "traceId", column = "trace_id"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by")
    })
    List<DSSqlValidateLog> findByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);
}
