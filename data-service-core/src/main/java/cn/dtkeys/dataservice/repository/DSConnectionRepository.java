package cn.dtkeys.dataservice.repository;

import cn.dtkeys.dataservice.datasource.model.DSConnection;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DSConnectionRepository {

    @Insert("""
        insert into ds_connection (
            connection_code, connection_name, db_type, host, port, username, password_ciphertext,
            status, remark, connection_config_json, deleted, created_by, updated_by
        ) values (
            #{connectionCode}, #{connectionName}, #{dbType}, #{host}, #{port}, #{username}, #{passwordCiphertext},
            #{status}, #{remark}, #{connectionConfigJson}, #{deleted}, #{createdBy}, #{updatedBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DSConnection connection);

    @Select("""
        select id, connection_code, connection_name, db_type, host, port, username, password_ciphertext,
               status, remark, connection_config_json, deleted, created_at, created_by, updated_at, updated_by
        from ds_connection
        where id = #{id} and deleted = false
        """)
    @Results(id = "dsConnectionByIdResult", value = {
        @Result(property = "connectionCode", column = "connection_code"),
        @Result(property = "connectionName", column = "connection_name"),
        @Result(property = "dbType", column = "db_type"),
        @Result(property = "passwordCiphertext", column = "password_ciphertext"),
        @Result(property = "connectionConfigJson", column = "connection_config_json"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    DSConnection findById(Long id);

    @Select("""
        select id, connection_code, connection_name, db_type, host, port, username, password_ciphertext,
               status, remark, connection_config_json, deleted, created_at, created_by, updated_at, updated_by
        from ds_connection
        where connection_code = #{connectionCode} and deleted = false
        """)
    @Results(id = "dsConnectionResult", value = {
        @Result(property = "connectionCode", column = "connection_code"),
        @Result(property = "connectionName", column = "connection_name"),
        @Result(property = "dbType", column = "db_type"),
        @Result(property = "passwordCiphertext", column = "password_ciphertext"),
        @Result(property = "connectionConfigJson", column = "connection_config_json"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    DSConnection findByCode(String connectionCode);

    @Select("""
        select id, connection_code, connection_name, db_type, host, port, username, password_ciphertext,
               status, remark, connection_config_json, deleted, created_at, created_by, updated_at, updated_by
        from ds_connection
        where deleted = false
        order by id
        """)
    @Results(id = "dsConnectionListResult", value = {
        @Result(property = "connectionCode", column = "connection_code"),
        @Result(property = "connectionName", column = "connection_name"),
        @Result(property = "dbType", column = "db_type"),
        @Result(property = "passwordCiphertext", column = "password_ciphertext"),
        @Result(property = "connectionConfigJson", column = "connection_config_json"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "createdBy", column = "created_by"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "updatedBy", column = "updated_by")
    })
    List<DSConnection> findAll();

    @Update("""
        update ds_connection
        set connection_name = #{connectionName},
            db_type = #{dbType},
            host = #{host},
            port = #{port},
            username = #{username},
            password_ciphertext = #{passwordCiphertext},
            status = #{status},
            remark = #{remark},
            connection_config_json = #{connectionConfigJson},
            updated_by = #{updatedBy},
            updated_at = current_timestamp
        where id = #{id} and deleted = false
        """)
    int update(DSConnection connection);

    @Update("""
        update ds_connection
        set deleted = true,
            updated_by = #{operator},
            updated_at = current_timestamp
        where id = #{id} and deleted = false
        """)
    int markDeleted(@Param("id") Long id, @Param("operator") String operator);
}
