package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.datasource.model.DSCatalog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DSCatalogRepository {

    @Insert("""
        insert into ds_catalog (
            connection_id, catalog_type, catalog_value, deleted, created_by, updated_by
        ) values (
            #{connectionId}, #{catalogType}, #{catalogValue}, #{deleted}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSCatalog catalog);

    @Select("""
        select id, connection_id, catalog_type, catalog_value, deleted, created_at, created_by, updated_at, updated_by
        from ds_catalog
        where id = #{id} and deleted = false
        """)
    @Results(id = "dsCatalogByIdResult", value = {
        @Result(property = "connectionId", column = "connection_id"),
        @Result(property = "catalogType", column = "catalog_type"),
        @Result(property = "catalogValue", column = "catalog_value"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    DSCatalog findById(Long id);

    @Select("""
        select id, connection_id, catalog_type, catalog_value, deleted, created_at, created_by, updated_at, updated_by
        from ds_catalog
        where connection_id = #{connectionId} and deleted = false
        order by id
        """)
    @Results(id = "dsCatalogResult", value = {
        @Result(property = "connectionId", column = "connection_id"),
        @Result(property = "catalogType", column = "catalog_type"),
        @Result(property = "catalogValue", column = "catalog_value"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    List<DSCatalog> findByConnectionId(Long connectionId);

    @Update("""
        update ds_catalog
        set catalog_type = #{catalogType},
            catalog_value = #{catalogValue},
            deleted = #{deleted},
            updated_by = #{updatedBy},
            updated_at = now()
        where id = #{id}
        """)
    int update(DSCatalog catalog);

    @Update("""
        update ds_catalog
        set deleted = true,
            updated_by = #{operator},
            updated_at = now()
        where id = #{id}
        """)
    int markDeleted(@Param("id") Long id, @Param("operator") String operator);

    @Update("""
        delete from ds_catalog
        where connection_id = #{connectionId}
        """)
    int deleteByConnectionId(Long connectionId);
}
