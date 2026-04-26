package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DsConnectionRepository {

    /**
     * 新增连接元数据。
     */
    @Insert("""
            insert into ds_connection (
                connection_code, connection_name, db_type, host, port, username,
                password_ciphertext, status, remark, connection_config_json,
                deleted, created_by, updated_by
            ) values (
                #{record.connectionCode}, #{record.connectionName}, #{record.dbType}, #{record.host}, #{record.port}, #{record.username},
                #{record.passwordCiphertext}, #{record.status}, #{record.remark}, #{record.connectionConfigJson},
                #{record.deleted}, #{record.createdBy}, #{record.updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id", keyColumn = "id")
    int insert(@Param("record") DsConnectionRecord record);

    /**
     * 按连接编码查询启用中的连接定义。
     */
    @Select("""
            select *
            from ds_connection
            where connection_code = #{connectionCode}
              and deleted = false
            """)
    DsConnectionRecord findByConnectionCode(@Param("connectionCode") String connectionCode);

    /**
     * 按状态查询连接列表。
     */
    @Select("""
            select *
            from ds_connection
            where status = #{status}
              and deleted = false
            order by created_at desc, id desc
            """)
    List<DsConnectionRecord> findByStatus(@Param("status") String status);

    /**
     * 更新连接状态。
     */
    @Update("""
            update ds_connection
            set status = #{status},
                updated_by = #{updatedBy},
                updated_at = current_timestamp
            where id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") String updatedBy);
}
