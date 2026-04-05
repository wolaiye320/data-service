package cn.dtkeys.dataservice.infrastructure.repository;

import cn.dtkeys.dataservice.domain.model.DSDefinition;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DSDefinitionRepository {

    @Insert("""
        insert into ds_service (
            service_code, service_name, service_type, status, sql_template, sql_type, execution_mode, plan_status,
            current_sql_version, version, max_batch_size, max_result_rows, query_timeout_seconds,
            federated_query_timeout_seconds, remark, deleted, published_at, published_by, created_by, updated_by
        ) values (
            #{serviceCode}, #{serviceName}, #{serviceType}, #{status}, #{sqlTemplate}, #{sqlType}, #{executionMode},
            #{planStatus}, #{currentSqlVersion}, #{version}, #{maxBatchSize}, #{maxResultRows},
            #{queryTimeoutSeconds}, #{federatedQueryTimeoutSeconds}, #{remark}, #{deleted},
            #{publishedAt}, #{publishedBy}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSDefinition definition);

    @Select("""
        select id, service_code, service_name, service_type, status, sql_template, sql_type, execution_mode,
               plan_status, current_sql_version, version, max_batch_size, max_result_rows, query_timeout_seconds,
               federated_query_timeout_seconds, remark, deleted, published_at, published_by,
               created_at, created_by, updated_at, updated_by
        from ds_service
        where service_code = #{serviceCode} and deleted = false
        """)
    @Results(id = "dsDefinitionResult", value = {
        @Result(property = "serviceCode", column = "service_code"),
        @Result(property = "serviceName", column = "service_name"),
        @Result(property = "serviceType", column = "service_type"),
        @Result(property = "sqlTemplate", column = "sql_template"),
        @Result(property = "sqlType", column = "sql_type"),
        @Result(property = "executionMode", column = "execution_mode"),
        @Result(property = "planStatus", column = "plan_status"),
        @Result(property = "currentSqlVersion", column = "current_sql_version"),
        @Result(property = "maxBatchSize", column = "max_batch_size"),
        @Result(property = "maxResultRows", column = "max_result_rows"),
        @Result(property = "queryTimeoutSeconds", column = "query_timeout_seconds"),
        @Result(property = "federatedQueryTimeoutSeconds", column = "federated_query_timeout_seconds"),
        @Result(property = "publishedAt", column = "published_at"),
        @Result(property = "publishedBy", column = "published_by"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    DSDefinition findByServiceCode(String serviceCode);

    @Select("""
        select id, service_code, service_name, service_type, status, sql_template, sql_type, execution_mode,
               plan_status, current_sql_version, version, max_batch_size, max_result_rows, query_timeout_seconds,
               federated_query_timeout_seconds, remark, deleted, published_at, published_by,
               created_at, created_by, updated_at, updated_by
        from ds_service
        where id = #{id} and deleted = false
        """)
    @Results(id = "dsDefinitionByIdResult", value = {
        @Result(property = "serviceCode", column = "service_code"),
        @Result(property = "serviceName", column = "service_name"),
        @Result(property = "serviceType", column = "service_type"),
        @Result(property = "sqlTemplate", column = "sql_template"),
        @Result(property = "sqlType", column = "sql_type"),
        @Result(property = "executionMode", column = "execution_mode"),
        @Result(property = "planStatus", column = "plan_status"),
        @Result(property = "currentSqlVersion", column = "current_sql_version"),
        @Result(property = "maxBatchSize", column = "max_batch_size"),
        @Result(property = "maxResultRows", column = "max_result_rows"),
        @Result(property = "queryTimeoutSeconds", column = "query_timeout_seconds"),
        @Result(property = "federatedQueryTimeoutSeconds", column = "federated_query_timeout_seconds"),
        @Result(property = "publishedAt", column = "published_at"),
        @Result(property = "publishedBy", column = "published_by"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    DSDefinition findById(Long id);

    @Select("""
        select id, service_code, service_name, service_type, status, sql_template, sql_type, execution_mode,
               plan_status, current_sql_version, version, max_batch_size, max_result_rows, query_timeout_seconds,
               federated_query_timeout_seconds, remark, deleted, published_at, published_by,
               created_at, created_by, updated_at, updated_by
        from ds_service
        where deleted = false
        order by id
        """)
    @Results(id = "dsDefinitionListResult", value = {
        @Result(property = "serviceCode", column = "service_code"),
        @Result(property = "serviceName", column = "service_name"),
        @Result(property = "serviceType", column = "service_type"),
        @Result(property = "sqlTemplate", column = "sql_template"),
        @Result(property = "sqlType", column = "sql_type"),
        @Result(property = "executionMode", column = "execution_mode"),
        @Result(property = "planStatus", column = "plan_status"),
        @Result(property = "currentSqlVersion", column = "current_sql_version"),
        @Result(property = "maxBatchSize", column = "max_batch_size"),
        @Result(property = "maxResultRows", column = "max_result_rows"),
        @Result(property = "queryTimeoutSeconds", column = "query_timeout_seconds"),
        @Result(property = "federatedQueryTimeoutSeconds", column = "federated_query_timeout_seconds"),
        @Result(property = "publishedAt", column = "published_at"),
        @Result(property = "publishedBy", column = "published_by"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    List<DSDefinition> findAll();

    @Update("""
        update ds_service
        set service_name = #{serviceName},
            service_type = #{serviceType},
            status = #{status},
            sql_template = #{sqlTemplate},
            sql_type = #{sqlType},
            execution_mode = #{executionMode},
            plan_status = #{planStatus},
            current_sql_version = #{currentSqlVersion},
            version = #{version},
            max_batch_size = #{maxBatchSize},
            max_result_rows = #{maxResultRows},
            query_timeout_seconds = #{queryTimeoutSeconds},
            federated_query_timeout_seconds = #{federatedQueryTimeoutSeconds},
            remark = #{remark},
            published_at = #{publishedAt},
            published_by = #{publishedBy},
            updated_by = #{updatedBy},
            updated_at = current_timestamp
        where id = #{id} and deleted = false
        """)
    int update(DSDefinition definition);
}
