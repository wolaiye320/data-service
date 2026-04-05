package cn.dtkeys.dataservice.infrastructure.repository;

import cn.dtkeys.dataservice.domain.model.DSCachePolicy;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DSCachePolicyRepository {

    @Insert("""
        insert into ds_cache_policy (
            service_id, enabled, ttl_seconds, cache_key_template, max_entries, remark, deleted, created_by, updated_by
        ) values (
            #{serviceId}, #{enabled}, #{ttlSeconds}, #{cacheKeyTemplate}, #{maxEntries}, #{remark}, #{deleted},
            #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSCachePolicy cachePolicy);

    @Select("""
        select id, service_id, enabled, ttl_seconds, cache_key_template, max_entries, remark, deleted,
               created_at, created_by, updated_at, updated_by
        from ds_cache_policy
        where service_id = #{serviceId} and deleted = false
        """)
    @Results(id = "dsCachePolicyResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "ttlSeconds", column = "ttl_seconds"),
        @Result(property = "cacheKeyTemplate", column = "cache_key_template"),
        @Result(property = "maxEntries", column = "max_entries"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    DSCachePolicy findByServiceId(Long serviceId);

    @Update("""
        update ds_cache_policy
        set enabled = #{enabled},
            ttl_seconds = #{ttlSeconds},
            cache_key_template = #{cacheKeyTemplate},
            max_entries = #{maxEntries},
            remark = #{remark},
            updated_by = #{updatedBy},
            updated_at = current_timestamp
        where service_id = #{serviceId} and deleted = false
        """)
    int updateByServiceId(DSCachePolicy cachePolicy);

    @Update("""
        update ds_cache_policy
        set deleted = true,
            updated_by = #{updatedBy},
            updated_at = current_timestamp
        where service_id = #{serviceId} and deleted = false
        """)
    int logicalDeleteByServiceId(@Param("serviceId") Long serviceId, @Param("updatedBy") String updatedBy);
}
