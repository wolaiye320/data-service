package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsDialectRuleRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DsDialectRuleRepository {

    /**
     * 新增方言规则。
     */
    @Insert("""
            insert into ds_dialect_rule (
                connection_id, db_type, rule_code, rule_type, rule_config, status, remark
            ) values (
                #{record.connectionId}, #{record.dbType}, #{record.ruleCode}, #{record.ruleType}, #{record.ruleConfig}, #{record.status}, #{record.remark}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsDialectRuleRecord record);

    /**
     * 查询连接级启用规则。
     */
    @Select("""
            select *
            from ds_dialect_rule
            where connection_id = #{connectionId}
              and status = 'ENABLED'
            order by rule_code asc, created_at asc, id asc
            """)
    List<DsDialectRuleRecord> findEnabledByConnectionId(@Param("connectionId") Long connectionId);

    /**
     * 查询数据库类型级启用规则。
     */
    @Select("""
            select *
            from ds_dialect_rule
            where db_type = #{dbType}
              and status = 'ENABLED'
            order by rule_code asc, created_at asc, id asc
            """)
    List<DsDialectRuleRecord> findEnabledByDbType(@Param("dbType") String dbType);
}
