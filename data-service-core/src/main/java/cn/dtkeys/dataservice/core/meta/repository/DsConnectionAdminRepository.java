package cn.dtkeys.dataservice.core.meta.repository;

import cn.dtkeys.dataservice.core.meta.domain.DsConnectionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DsConnectionAdminRepository {

    /**
     * 按主键查询连接。
     */
    @Select("""
            select *
            from ds_connection
            where id = #{id}
              and deleted = false
            """)
    DsConnectionRecord findById(@Param("id") Long id);

    /**
     * 查询连接列表。
     */
    @Select("""
            <script>
            select *
            from ds_connection
            where deleted = false
            <if test="status != null and status != ''">
              and status = #{status}
            </if>
            order by created_at desc, id desc
            </script>
            """)
    List<DsConnectionRecord> findAll(@Param("status") String status);

    /**
     * 更新连接基础信息。
     */
    @Update("""
            update ds_connection
            set connection_name = #{record.connectionName},
                db_type = #{record.dbType},
                host = #{record.host},
                port = #{record.port},
                username = #{record.username},
                password_ciphertext = #{record.passwordCiphertext},
                remark = #{record.remark},
                connection_config_json = #{record.connectionConfigJson},
                updated_by = #{record.updatedBy},
                updated_at = current_timestamp
            where id = #{record.id}
            """)
    int update(@Param("record") DsConnectionRecord record);
}
