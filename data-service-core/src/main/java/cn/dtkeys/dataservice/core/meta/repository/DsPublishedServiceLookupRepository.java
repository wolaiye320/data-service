package cn.dtkeys.dataservice.core.meta.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DsPublishedServiceLookupRepository {

    /**
     * 统计已发布服务引用连接数量。
     */
    @Select("""
            select count(1)
            from ds_service_version v
            join ds_service s on s.id = v.service_id
            where s.deleted = false
              and s.status = 'PUBLISHED'
              and v.status = 'PUBLISHED'
              and v.source_snapshot_json like concat('%\"connectionCode\":\"', #{connectionCode}, '\"%')
            """)
    long countPublishedServicesUsingConnection(@Param("connectionCode") String connectionCode);
}
