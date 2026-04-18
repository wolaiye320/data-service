package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.service.model.DSSqlText;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DSSqlTextRepository {

    @Insert("""
        insert into ds_sql_text (
            service_id, version, sql_text, sql_comment, status, is_current, created_by, updated_by
        ) values (
            #{serviceId}, #{version}, #{sqlText}, #{sqlComment}, #{status}, #{current}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSSqlText sqlText);

    @Delete("""
        delete from ds_sql_text
        where service_id = #{serviceId} and status = 'DRAFT'
        """)
    int deleteDraftByServiceId(Long serviceId);

    @Select("""
        select id, service_id, version, sql_text, sql_comment, status, is_current,
               created_by, created_at, updated_by, updated_at
        from ds_sql_text
        where service_id = #{serviceId} and status = 'DRAFT'
        order by version desc, id desc
        limit 1
        """)
    @Results(id = "dsSqlTextResult", value = {
        @Result(property = "serviceId", column = "service_id"),
        @Result(property = "sqlText", column = "sql_text"),
        @Result(property = "sqlComment", column = "sql_comment"),
        @Result(property = "current", column = "is_current"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedBy", column = "updated_by"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    DSSqlText findLatestDraftByServiceId(Long serviceId);

    @Select("""
        select id, service_id, version, sql_text, sql_comment, status, is_current,
               created_by, created_at, updated_by, updated_at
        from ds_sql_text
        where service_id = #{serviceId}
          and version = #{version}
        limit 1
        """)
    @ResultMap("dsSqlTextResult")
    DSSqlText findByServiceIdAndVersion(@Param("serviceId") Long serviceId, @Param("version") Integer version);

    @Select("""
        select id, service_id, version, sql_text, sql_comment, status, is_current,
               created_by, created_at, updated_by, updated_at
        from ds_sql_text
        where service_id = #{serviceId}
          and is_current = true
        order by version desc, id desc
        limit 1
        """)
    @ResultMap("dsSqlTextResult")
    DSSqlText findCurrentPublishedByServiceId(Long serviceId);

    @Update("""
        update ds_sql_text
        set is_current = false,
            updated_at = current_timestamp,
            updated_by = #{updatedBy}
        where service_id = #{serviceId}
          and is_current = true
        """)
    int clearCurrentVersion(@Param("serviceId") Long serviceId, @Param("updatedBy") String updatedBy);

    @Update("""
        update ds_sql_text
        set status = 'PUBLISHED',
            is_current = true,
            updated_at = current_timestamp,
            updated_by = #{updatedBy}
        where service_id = #{serviceId}
          and version = #{version}
        """)
    int markPublished(@Param("serviceId") Long serviceId,
                      @Param("version") Integer version,
                      @Param("updatedBy") String updatedBy);
}
