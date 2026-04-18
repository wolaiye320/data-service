do
$$
begin
    if exists (
        select 1
        from information_schema.tables
        where table_schema = 'public'
          and table_name = 'ds'
    ) and not exists (
        select 1
        from information_schema.tables
        where table_schema = 'public'
          and table_name = 'ds_service'
    ) then
        execute 'alter table ds rename to ds_service';
    end if;
end
$$;

do
$$
begin
    if exists (
        select 1
        from pg_constraint
        where conname = 'ds_pkey'
    ) then
        execute 'alter table ds_service rename constraint ds_pkey to ds_service_pkey';
    end if;
end
$$;

do
$$
begin
    if exists (
        select 1
        from pg_constraint
        where conname = 'uk_ds_service_code'
    ) then
        execute 'alter table ds_service rename constraint uk_ds_service_code to uk_ds_service_service_code';
    end if;
end
$$;

do
$$
begin
    if exists (
        select 1
        from pg_class
        where relkind = 'S'
          and relname = 'ds_id_seq'
    ) then
        execute 'alter sequence ds_id_seq rename to ds_service_id_seq';
    end if;
end
$$;

comment on table ds_connection is '数据源连接表，记录外部数据库连接配置与连接状态。';
comment on column ds_connection.id is '主键。';
comment on column ds_connection.connection_code is '数据源连接编码，全局唯一。';
comment on column ds_connection.connection_name is '数据源连接名称。';
comment on column ds_connection.db_type is '数据库类型，如 POSTGRESQL、MYSQL、ORACLE。';
comment on column ds_connection.host is '数据库主机地址。';
comment on column ds_connection.port is '数据库端口。';
comment on column ds_connection.username is '数据库用户名。';
comment on column ds_connection.password_ciphertext is '加密后的数据库密码密文。';
comment on column ds_connection.status is '连接状态，如 ENABLED、DISABLED。';
comment on column ds_connection.remark is '备注说明。';
comment on column ds_connection.connection_config_json is '连接扩展配置 JSON。';
comment on column ds_connection.deleted is '逻辑删除标记。';
comment on column ds_connection.created_at is '创建时间。';
comment on column ds_connection.created_by is '创建人。';
comment on column ds_connection.updated_at is '最后更新时间。';
comment on column ds_connection.updated_by is '最后更新人。';

comment on table ds_catalog is '目标库映射表，定义连接下可访问的 database 或 schema。';
comment on column ds_catalog.id is '主键。';
comment on column ds_catalog.connection_id is '所属数据源连接主键。';
comment on column ds_catalog.catalog_code is '目标库编码，在同一连接内唯一。';
comment on column ds_catalog.catalog_name is '目标库名称。';
comment on column ds_catalog.catalog_type is '目标库类型，如 DATABASE、SCHEMA。';
comment on column ds_catalog.catalog_value is '目标库实际值，如数据库名或 schema 名。';
comment on column ds_catalog.status is '目标库状态，如 ENABLED、DISABLED。';
comment on column ds_catalog.remark is '备注说明。';
comment on column ds_catalog.deleted is '逻辑删除标记。';
comment on column ds_catalog.created_at is '创建时间。';
comment on column ds_catalog.created_by is '创建人。';
comment on column ds_catalog.updated_at is '最后更新时间。';
comment on column ds_catalog.updated_by is '最后更新人。';

comment on table ds_service is '数据服务定义主表，记录逻辑数据服务的基础信息、SQL 与发布状态。';
comment on column ds_service.id is '主键。';
comment on column ds_service.service_code is '数据服务编码，全局唯一。';
comment on column ds_service.service_name is '数据服务名称。';
comment on column ds_service.service_type is '数据服务类型，如 SIMPLE_QUERY、FEDERATED_QUERY。';
comment on column ds_service.status is '数据服务状态，如 DRAFT、PUBLISHED、DISABLED。';
comment on column ds_service.sql_template is '服务当前维护的 SQL 模板。';
comment on column ds_service.sql_type is 'SQL 类型，如 SIMPLE_SQL、FEDERATED_SQL。';
comment on column ds_service.execution_mode is '执行模式，如 REMOTE_ONLY、REMOTE_PLUS_LOCAL。';
comment on column ds_service.plan_status is '计划状态，如 UNPLANNED、VALIDATED、PLANNED、PUBLISHED。';
comment on column ds_service.current_sql_version is '当前已发布 SQL 版本号。';
comment on column ds_service.version is '服务定义版本号。';
comment on column ds_service.max_batch_size is '服务级批量查询上限。';
comment on column ds_service.max_result_rows is '服务级结果集行数上限。';
comment on column ds_service.query_timeout_seconds is '服务级单次查询超时秒数。';
comment on column ds_service.federated_query_timeout_seconds is '服务级联邦查询超时秒数。';
comment on column ds_service.remark is '备注说明。';
comment on column ds_service.deleted is '逻辑删除标记。';
comment on column ds_service.published_at is '最近一次发布时间。';
comment on column ds_service.published_by is '最近一次发布人。';
comment on column ds_service.created_at is '创建时间。';
comment on column ds_service.created_by is '创建人。';
comment on column ds_service.updated_at is '最后更新时间。';
comment on column ds_service.updated_by is '最后更新人。';

comment on table ds_source is '数据服务来源表，定义服务依赖的数据源、目标库与来源对象。';
comment on column ds_source.id is '主键。';
comment on column ds_source.service_id is '所属数据服务主键。';
comment on column ds_source.connection_id is '所属数据源连接主键。';
comment on column ds_source.catalog_id is '所属目标库主键，可为空。';
comment on column ds_source.source_alias is '来源别名，在同一服务内唯一。';
comment on column ds_source.source_type is '来源类型，如 TABLE、VIEW、SQL。';
comment on column ds_source.source_value is '来源实际值，如表名、视图名或片段标识。';
comment on column ds_source.join_key is '跨来源关联键定义。';
comment on column ds_source.config_json is '来源扩展配置 JSON。';
comment on column ds_source.status is '来源状态，如 ENABLED、DISABLED。';
comment on column ds_source.remark is '备注说明。';
comment on column ds_source.deleted is '逻辑删除标记。';
comment on column ds_source.created_at is '创建时间。';
comment on column ds_source.created_by is '创建人。';
comment on column ds_source.updated_at is '最后更新时间。';
comment on column ds_source.updated_by is '最后更新人。';

comment on table ds_param is '数据服务参数表，定义服务入参与 SQL 占位符映射。';
comment on column ds_param.id is '主键。';
comment on column ds_param.service_id is '所属数据服务主键。';
comment on column ds_param.param_name is '参数名称，在同一服务内唯一。';
comment on column ds_param.display_name is '参数展示名称。';
comment on column ds_param.param_type is '参数类型，如 STRING、LONG、BOOLEAN。';
comment on column ds_param.sql_placeholder is 'SQL 模板中的命名占位符。';
comment on column ds_param.required is '是否必填。';
comment on column ds_param.default_value is '默认值。';
comment on column ds_param.sort_order is '参数排序序号。';
comment on column ds_param.remark is '备注说明。';
comment on column ds_param.deleted is '逻辑删除标记。';
comment on column ds_param.created_at is '创建时间。';
comment on column ds_param.created_by is '创建人。';
comment on column ds_param.updated_at is '最后更新时间。';
comment on column ds_param.updated_by is '最后更新人。';

comment on table ds_field is '数据服务输出字段表，定义查询结果字段映射与展示信息。';
comment on column ds_field.id is '主键。';
comment on column ds_field.service_id is '所属数据服务主键。';
comment on column ds_field.source_alias is '来源别名。';
comment on column ds_field.source_column is '来源字段名。';
comment on column ds_field.field_name is '输出字段名，在同一服务内唯一。';
comment on column ds_field.display_name is '输出字段展示名称。';
comment on column ds_field.field_type is '输出字段类型。';
comment on column ds_field.sort_order is '字段排序序号。';
comment on column ds_field.primary_key is '是否主键字段。';
comment on column ds_field.join_key is '是否关联键字段。';
comment on column ds_field.remark is '备注说明。';
comment on column ds_field.deleted is '逻辑删除标记。';
comment on column ds_field.created_at is '创建时间。';
comment on column ds_field.created_by is '创建人。';
comment on column ds_field.updated_at is '最后更新时间。';
comment on column ds_field.updated_by is '最后更新人。';

comment on table ds_cache_policy is '数据服务缓存策略表，定义服务缓存开关、TTL 与容量。';
comment on column ds_cache_policy.id is '主键。';
comment on column ds_cache_policy.service_id is '所属数据服务主键。';
comment on column ds_cache_policy.enabled is '是否启用缓存。';
comment on column ds_cache_policy.ttl_seconds is '缓存过期秒数。';
comment on column ds_cache_policy.cache_key_template is '缓存键模板。';
comment on column ds_cache_policy.max_entries is '缓存最大条目数。';
comment on column ds_cache_policy.remark is '备注说明。';
comment on column ds_cache_policy.deleted is '逻辑删除标记。';
comment on column ds_cache_policy.created_at is '创建时间。';
comment on column ds_cache_policy.created_by is '创建人。';
comment on column ds_cache_policy.updated_at is '最后更新时间。';
comment on column ds_cache_policy.updated_by is '最后更新人。';
