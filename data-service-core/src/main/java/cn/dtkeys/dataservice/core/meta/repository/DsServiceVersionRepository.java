package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsServiceVersionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DsServiceVersionRepository {

    /**
     * 新增服务版本快照。
     */
    @Insert("""
            insert into ds_service_version (
                service_id, version, status, sql_type, sql_text,
                source_snapshot_json, param_snapshot_json, field_snapshot_json,
                validation_snapshot_json, plan_snapshot_json, request_context_snapshot_json,
                published_at, published_by, created_by, updated_by
            ) values (
                #{record.serviceId}, #{record.version}, #{record.status}, #{record.sqlType}, #{record.sqlText},
                #{record.sourceSnapshotJson}, #{record.paramSnapshotJson}, #{record.fieldSnapshotJson},
                #{record.validationSnapshotJson}, #{record.planSnapshotJson}, #{record.requestContextSnapshotJson},
                #{record.publishedAt}, #{record.publishedBy}, #{record.createdBy}, #{record.updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsServiceVersionRecord record);

    /**
     * 按服务和版本号查询版本快照。
     */
    @Select("""
            select *
            from ds_service_version
            where service_id = #{serviceId}
              and version = #{version}
            """)
    DsServiceVersionRecord findByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);

    /**
     * 查询服务历史版本。
     */
    @Select("""
            select *
            from ds_service_version
            where service_id = #{serviceId}
            order by version desc, id desc
            """)
    List<DsServiceVersionRecord> findHistoryByServiceId(@Param("serviceId") Long serviceId);

    /**
     * 查询最新草稿版本。
     */
    @Select("""
            select *
            from ds_service_version
            where service_id = #{serviceId}
              and status = 'DRAFT'
            order by version desc, id desc
            limit 1
            """)
    DsServiceVersionRecord findLatestDraftByServiceId(@Param("serviceId") Long serviceId);

    /**
     * 更新草稿版本内容。
     */
    @Update("""
            update ds_service_version
            set sql_type = #{record.sqlType},
                sql_text = #{record.sqlText},
                source_snapshot_json = #{record.sourceSnapshotJson},
                param_snapshot_json = #{record.paramSnapshotJson},
                field_snapshot_json = #{record.fieldSnapshotJson},
                validation_snapshot_json = #{record.validationSnapshotJson},
                plan_snapshot_json = #{record.planSnapshotJson},
                request_context_snapshot_json = #{record.requestContextSnapshotJson},
                updated_by = #{record.updatedBy},
                updated_at = current_timestamp
            where id = #{record.id}
            """)
    int updateDraft(@Param("record") DsServiceVersionRecord record);

    /**
     * 更新发布状态和发布审计字段。
     */
    @Update("""
            update ds_service_version
            set status = #{status},
                published_at = #{publishedAt},
                published_by = #{publishedBy},
                updated_by = #{updatedBy},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updatePublishState(@Param("id") Long id,
                           @Param("status") String status,
                           @Param("publishedAt") java.time.LocalDateTime publishedAt,
                           @Param("publishedBy") String publishedBy,
                           @Param("updatedBy") String updatedBy);

    /**
     * 清理旧的已发布状态。
     */
    @Update("""
            update ds_service_version
            set status = 'DISABLED',
                updated_by = #{updatedBy},
                updated_at = current_timestamp
            where service_id = #{serviceId}
              and status = 'PUBLISHED'
            """)
    int clearPublishedState(@Param("serviceId") Long serviceId, @Param("updatedBy") String updatedBy);
}
