package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsAuditLogRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DsAuditLogRepository {

    /**
     * 写入审计日志。
     */
    @Insert("""
            insert into ds_audit_log (
                service_id, connection_id, event_type, target_type, target_id,
                operator, operator_role, operation_result, trace_id, request_ip,
                change_summary, detail_json, context_summary_json, created_by
            ) values (
                #{record.serviceId}, #{record.connectionId}, #{record.eventType}, #{record.targetType}, #{record.targetId},
                #{record.operator}, #{record.operatorRole}, #{record.operationResult}, #{record.traceId}, #{record.requestIp},
                #{record.changeSummary}, #{record.detailJson}, #{record.contextSummaryJson}, #{record.createdBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsAuditLogRecord record);

    /**
     * 按服务查询审计流水。
     */
    @Select("""
            select *
            from ds_audit_log
            where service_id = #{serviceId}
            order by created_at desc, id desc
            """)
    List<DsAuditLogRecord> findByServiceId(@Param("serviceId") Long serviceId);

    /**
     * 按连接查询审计流水。
     */
    @Select("""
            select *
            from ds_audit_log
            where connection_id = #{connectionId}
            order by created_at desc, id desc
            """)
    List<DsAuditLogRecord> findByConnectionId(@Param("connectionId") Long connectionId);

    /**
     * 按筛选条件分页查询审计流水。
     */
    @Select({
            "<script>",
            "select *",
            "from ds_audit_log",
            "<where>",
            "  <if test='serviceId != null'>and service_id = #{serviceId}</if>",
            "  <if test='operator != null and operator != \"\"'>and operator = #{operator}</if>",
            "  <if test='eventType != null and eventType != \"\"'>and event_type = #{eventType}</if>",
            "  <if test='operationResult != null and operationResult != \"\"'>and operation_result = #{operationResult}</if>",
            "  <if test='traceId != null and traceId != \"\"'>and trace_id = #{traceId}</if>",
            "  <if test='startAt != null'>and created_at &gt;= #{startAt}</if>",
            "  <if test='endAt != null'>and created_at &lt;= #{endAt}</if>",
            "</where>",
            "order by created_at desc, id desc",
            "limit #{limit} offset #{offset}",
            "</script>"
    })
    List<DsAuditLogRecord> findByFilters(@Param("serviceId") Long serviceId,
                                         @Param("operator") String operator,
                                         @Param("eventType") String eventType,
                                         @Param("operationResult") String operationResult,
                                         @Param("traceId") String traceId,
                                         @Param("startAt") java.time.LocalDateTime startAt,
                                         @Param("endAt") java.time.LocalDateTime endAt,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 按筛选条件统计审计流水。
     */
    @Select({
            "<script>",
            "select count(1)",
            "from ds_audit_log",
            "<where>",
            "  <if test='serviceId != null'>and service_id = #{serviceId}</if>",
            "  <if test='operator != null and operator != \"\"'>and operator = #{operator}</if>",
            "  <if test='eventType != null and eventType != \"\"'>and event_type = #{eventType}</if>",
            "  <if test='operationResult != null and operationResult != \"\"'>and operation_result = #{operationResult}</if>",
            "  <if test='traceId != null and traceId != \"\"'>and trace_id = #{traceId}</if>",
            "  <if test='startAt != null'>and created_at &gt;= #{startAt}</if>",
            "  <if test='endAt != null'>and created_at &lt;= #{endAt}</if>",
            "</where>",
            "</script>"
    })
    long countByFilters(@Param("serviceId") Long serviceId,
                        @Param("operator") String operator,
                        @Param("eventType") String eventType,
                        @Param("operationResult") String operationResult,
                        @Param("traceId") String traceId,
                        @Param("startAt") java.time.LocalDateTime startAt,
                        @Param("endAt") java.time.LocalDateTime endAt);

    /**
     * 按主键查询审计详情。
     */
    @Select("""
            select *
            from ds_audit_log
            where id = #{id}
            """)
    DsAuditLogRecord findById(@Param("id") Long id);
}
