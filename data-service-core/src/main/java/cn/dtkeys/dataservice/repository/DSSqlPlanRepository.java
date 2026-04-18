package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.service.model.DSSqlPlan;
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
public interface DSSqlPlanRepository {

    @Insert("""
        insert into ds_sql_plan (
            service_id, version, plan_stage, plan_format, plan_content, stage_graph_json,
            pushdown_summary, fallback_reason, cost_summary, local_execution_summary,
            datasource_scope, created_by
        ) values (
            #{serviceId}, #{version}, #{planStage}, #{planFormat}, #{planContent}, #{stageGraphJson},
            #{pushdownSummary}, #{fallbackReason}, #{costSummary}, #{localExecutionSummary},
            #{datasourceScope}, #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSSqlPlan sqlPlan);

    @Delete("""
        delete from ds_sql_plan
        where service_id = #{serviceId} and version = #{version}
        """)
    int deleteByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);

    @Select("""
        select id, service_id, version, plan_stage, plan_format, plan_content, stage_graph_json,
               pushdown_summary, fallback_reason, cost_summary, local_execution_summary,
               datasource_scope, created_at, created_by
        from ds_sql_plan
        where service_id = #{serviceId} and version = #{version}
        order by id
        """)
    @Results(id = "dsSqlPlanResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "planStage", column = "plan_stage"),
        @Result(property = "planFormat", column = "plan_format"),
        @Result(property = "planContent", column = "plan_content"),
        @Result(property = "stageGraphJson", column = "stage_graph_json"),
        @Result(property = "pushdownSummary", column = "pushdown_summary"),
        @Result(property = "fallbackReason", column = "fallback_reason"),
        @Result(property = "costSummary", column = "cost_summary"),
        @Result(property = "localExecutionSummary", column = "local_execution_summary"),
        @Result(property = "datasourceScope", column = "datasource_scope"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by")
    })
    List<DSSqlPlan> findByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);
}
