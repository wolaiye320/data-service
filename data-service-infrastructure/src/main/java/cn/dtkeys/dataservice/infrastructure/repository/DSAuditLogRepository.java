package cn.dtkeys.dataservice.infrastructure.repository;

import cn.dtkeys.dataservice.domain.model.DSAuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DSAuditLogRepository {

    @Insert("""
        insert into ds_audit_log (
            service_id, connection_id, event_type, target_type, target_id, operator, operator_role,
            operation_result, trace_id, request_ip, change_summary, detail_json, created_at, created_by
        ) values (
            #{serviceId}, #{connectionId}, #{eventType}, #{targetType}, #{targetId}, #{operator}, #{operatorRole},
            #{operationResult}, #{traceId}, #{requestIp}, #{changeSummary}, #{detailJson}, #{createdAt}, #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSAuditLog auditLog);

    @Select("""
        <script>
        select id, service_id, connection_id, event_type, target_type, target_id, operator, operator_role,
               operation_result, trace_id, request_ip, change_summary, detail_json, created_at, created_by
        from ds_audit_log
        <where>
            <if test="serviceId != null">and service_id = #{serviceId}</if>
            <if test="connectionId != null">and connection_id = #{connectionId}</if>
            <if test="operator != null and operator != ''">and operator = #{operator}</if>
            <if test="eventType != null and eventType != ''">and event_type = #{eventType}</if>
            <if test="operationResult != null and operationResult != ''">and operation_result = #{operationResult}</if>
            <if test="startTime != null">and created_at &gt;= #{startTime}</if>
            <if test="endTime != null">and created_at &lt;= #{endTime}</if>
        </where>
        order by created_at desc, id desc
        limit #{pageSize} offset #{offset}
        </script>
        """)
    @Results(id = "auditLogResultMap", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "connectionId", column = "connection_id"),
        @Result(property = "eventType", column = "event_type"),
        @Result(property = "targetType", column = "target_type"),
        @Result(property = "targetId", column = "target_id"),
        @Result(property = "operator", column = "operator"),
        @Result(property = "operatorRole", column = "operator_role"),
        @Result(property = "operationResult", column = "operation_result"),
        @Result(property = "traceId", column = "trace_id"),
        @Result(property = "requestIp", column = "request_ip"),
        @Result(property = "changeSummary", column = "change_summary"),
        @Result(property = "detailJson", column = "detail_json"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by")
    })
    List<DSAuditLog> search(@Param("serviceId") Long serviceId,
                            @Param("connectionId") Long connectionId,
                            @Param("operator") String operator,
                            @Param("eventType") String eventType,
                            @Param("operationResult") String operationResult,
                            @Param("startTime") java.time.LocalDateTime startTime,
                            @Param("endTime") java.time.LocalDateTime endTime,
                            @Param("pageSize") int pageSize,
                            @Param("offset") int offset);

    @Select("""
        <script>
        select count(1)
        from ds_audit_log
        <where>
            <if test="serviceId != null">and service_id = #{serviceId}</if>
            <if test="connectionId != null">and connection_id = #{connectionId}</if>
            <if test="operator != null and operator != ''">and operator = #{operator}</if>
            <if test="eventType != null and eventType != ''">and event_type = #{eventType}</if>
            <if test="operationResult != null and operationResult != ''">and operation_result = #{operationResult}</if>
            <if test="startTime != null">and created_at &gt;= #{startTime}</if>
            <if test="endTime != null">and created_at &lt;= #{endTime}</if>
        </where>
        </script>
        """)
    long count(@Param("serviceId") Long serviceId,
               @Param("connectionId") Long connectionId,
               @Param("operator") String operator,
               @Param("eventType") String eventType,
               @Param("operationResult") String operationResult,
               @Param("startTime") java.time.LocalDateTime startTime,
               @Param("endTime") java.time.LocalDateTime endTime);

    @Select("""
        select id, service_id, connection_id, event_type, target_type, target_id, operator, operator_role,
               operation_result, trace_id, request_ip, change_summary, detail_json, created_at, created_by
        from ds_audit_log
        where id = #{id}
        """)
    @Results(id = "auditLogDetailResultMap", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "connectionId", column = "connection_id"),
        @Result(property = "eventType", column = "event_type"),
        @Result(property = "targetType", column = "target_type"),
        @Result(property = "targetId", column = "target_id"),
        @Result(property = "operator", column = "operator"),
        @Result(property = "operatorRole", column = "operator_role"),
        @Result(property = "operationResult", column = "operation_result"),
        @Result(property = "traceId", column = "trace_id"),
        @Result(property = "requestIp", column = "request_ip"),
        @Result(property = "changeSummary", column = "change_summary"),
        @Result(property = "detailJson", column = "detail_json"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by")
    })
    DSAuditLog findById(Long id);
}
