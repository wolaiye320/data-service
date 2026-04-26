package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsCachePolicyRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DsCachePolicyRepository {

    /**
     * 新增缓存策略。
     */
    @Insert("""
            insert into ds_cache_policy (
                service_id, enabled, ttl_seconds, cache_key_template, max_entries,
                context_keys_json, remark, deleted, created_by, updated_by
            ) values (
                #{record.serviceId}, #{record.enabled}, #{record.ttlSeconds}, #{record.cacheKeyTemplate}, #{record.maxEntries},
                #{record.contextKeysJson}, #{record.remark}, #{record.deleted}, #{record.createdBy}, #{record.updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsCachePolicyRecord record);

    /**
     * 按服务查询缓存策略。
     */
    @Select("""
            select *
            from ds_cache_policy
            where service_id = #{serviceId}
              and deleted = false
            """)
    DsCachePolicyRecord findByServiceId(@Param("serviceId") Long serviceId);

    /**
     * 更新缓存策略核心字段。
     */
    @Update("""
            update ds_cache_policy
            set enabled = #{enabled},
                ttl_seconds = #{ttlSeconds},
                cache_key_template = #{cacheKeyTemplate},
                max_entries = #{maxEntries},
                context_keys_json = #{contextKeysJson},
                remark = #{remark},
                updated_by = #{updatedBy},
                updated_at = current_timestamp
            where service_id = #{serviceId}
            """)
    int updatePolicy(@Param("serviceId") Long serviceId,
                     @Param("enabled") Boolean enabled,
                     @Param("ttlSeconds") Integer ttlSeconds,
                     @Param("cacheKeyTemplate") String cacheKeyTemplate,
                     @Param("maxEntries") Integer maxEntries,
                     @Param("contextKeysJson") String contextKeysJson,
                     @Param("remark") String remark,
                     @Param("updatedBy") String updatedBy);
}
