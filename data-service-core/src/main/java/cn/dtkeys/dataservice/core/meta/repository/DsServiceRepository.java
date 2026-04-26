package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsServiceRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DsServiceRepository {

    /**
     * 新增服务主记录。
     */
    @Insert("""
            insert into ds_service (
                service_code, service_name, sql_type, default_connection_code, status, current_version,
                max_batch_size, max_result_rows, query_timeout_seconds, federated_query_timeout_seconds,
                tenant_id, remark, deleted, created_by, updated_by
            ) values (
                #{record.serviceCode}, #{record.serviceName}, #{record.sqlType}, #{record.defaultConnectionCode}, #{record.status}, #{record.currentVersion},
                #{record.maxBatchSize}, #{record.maxResultRows}, #{record.queryTimeoutSeconds}, #{record.federatedQueryTimeoutSeconds},
                #{record.tenantId}, #{record.remark}, #{record.deleted}, #{record.createdBy}, #{record.updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsServiceRecord record);

    /**
     * 按服务编码查询服务主记录。
     */
    @Select("""
            select *
            from ds_service
            where service_code = #{serviceCode}
              and deleted = false
            """)
    DsServiceRecord findByServiceCode(@Param("serviceCode") String serviceCode);

    /**
     * 按主键查询服务主记录。
     */
    @Select("""
            select *
            from ds_service
            where id = #{id}
              and deleted = false
            """)
    DsServiceRecord findById(@Param("id") Long id);

    /**
     * 按状态查询服务列表。
     */
    @Select("""
            select *
            from ds_service
            where status = #{status}
              and deleted = false
            order by created_at desc, id desc
            """)
    List<DsServiceRecord> findByStatus(@Param("status") String status);

    /**
     * 查询全部服务列表。
     */
    @Select("""
            select *
            from ds_service
            where deleted = false
            order by created_at desc, id desc
            """)
    List<DsServiceRecord> findAll();

    /**
     * 更新服务基础信息。
     */
    @Update("""
            update ds_service
            set service_name = #{record.serviceName},
                sql_type = #{record.sqlType},
                default_connection_code = #{record.defaultConnectionCode},
                max_batch_size = #{record.maxBatchSize},
                max_result_rows = #{record.maxResultRows},
                query_timeout_seconds = #{record.queryTimeoutSeconds},
                federated_query_timeout_seconds = #{record.federatedQueryTimeoutSeconds},
                tenant_id = #{record.tenantId},
                remark = #{record.remark},
                updated_by = #{record.updatedBy},
                updated_at = current_timestamp
            where id = #{record.id}
            """)
    int update(@Param("record") DsServiceRecord record);

    /**
     * 更新当前发布版本号。
     */
    @Update("""
            update ds_service
            set current_version = #{currentVersion},
                updated_by = #{updatedBy},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateCurrentVersion(@Param("id") Long id, @Param("currentVersion") Integer currentVersion, @Param("updatedBy") String updatedBy);

    /**
     * 更新服务状态。
     */
    @Update("""
            update ds_service
            set status = #{status},
                updated_by = #{updatedBy},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") String updatedBy);
}
