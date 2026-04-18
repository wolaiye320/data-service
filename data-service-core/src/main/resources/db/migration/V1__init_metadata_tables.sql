create table if not exists ds_connection (
    id bigserial primary key,
    connection_code varchar(64) not null,
    connection_name varchar(128) not null,
    db_type varchar(32) not null,
    host varchar(255) not null,
    port integer not null,
    username varchar(128) not null,
    password_ciphertext varchar(512) not null,
    status varchar(32) not null,
    remark varchar(512),
    connection_config_json text,
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_connection_code unique (connection_code)
);

create table if not exists ds_catalog (
    id bigserial primary key,
    connection_id bigint not null references ds_connection (id),
    catalog_code varchar(128) not null,
    catalog_name varchar(128) not null,
    catalog_type varchar(32) not null default 'DATABASE',
    catalog_value varchar(256) not null,
    status varchar(32) not null default 'ENABLED',
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_catalog_code unique (connection_id, catalog_code)
);

create table if not exists ds (
    id bigserial primary key,
    service_code varchar(64) not null,
    service_name varchar(128) not null,
    service_type varchar(32) not null default 'SIMPLE_QUERY',
    status varchar(32) not null default 'DRAFT',
    sql_template text,
    sql_type varchar(32) not null default 'SIMPLE_SQL',
    execution_mode varchar(32) not null default 'REMOTE_ONLY',
    plan_status varchar(32) not null default 'UNPLANNED',
    current_sql_version integer,
    version integer not null default 0,
    max_batch_size integer,
    max_result_rows integer,
    query_timeout_seconds integer,
    federated_query_timeout_seconds integer,
    remark varchar(512),
    deleted boolean not null default false,
    published_at timestamp,
    published_by varchar(64),
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_service_code unique (service_code)
);

create table if not exists ds_source (
    id bigserial primary key,
    service_id bigint not null references ds (id),
    connection_id bigint not null references ds_connection (id),
    catalog_id bigint references ds_catalog (id),
    source_alias varchar(64) not null,
    source_type varchar(32) not null default 'TABLE',
    source_value varchar(256) not null,
    join_key varchar(128),
    config_json text,
    status varchar(32) not null default 'ENABLED',
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_source_alias unique (service_id, source_alias)
);

create table if not exists ds_param (
    id bigserial primary key,
    service_id bigint not null references ds (id),
    param_name varchar(64) not null,
    display_name varchar(128) not null,
    param_type varchar(32) not null,
    sql_placeholder varchar(128) not null,
    required boolean not null default true,
    default_value varchar(512),
    sort_order integer not null default 0,
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_param_name unique (service_id, param_name)
);

create table if not exists ds_field (
    id bigserial primary key,
    service_id bigint not null references ds (id),
    source_alias varchar(64),
    source_column varchar(128) not null,
    field_name varchar(64) not null,
    display_name varchar(128) not null,
    field_type varchar(32) not null,
    sort_order integer not null default 0,
    primary_key boolean not null default false,
    join_key boolean not null default false,
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_field_name unique (service_id, field_name)
);

create table if not exists ds_cache_policy (
    id bigserial primary key,
    service_id bigint not null references ds (id),
    enabled boolean not null default false,
    ttl_seconds integer not null default 300,
    cache_key_template varchar(512) not null default 'data-service:{serviceCode}:v{version}:{paramHash}',
    max_entries integer not null default 1000,
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_cache_policy_service unique (service_id)
);

create index if not exists idx_ds_catalog_connection_id on ds_catalog (connection_id);
create index if not exists idx_ds_source_service_id on ds_source (service_id);
create index if not exists idx_ds_source_connection_id on ds_source (connection_id);
create index if not exists idx_ds_param_service_id on ds_param (service_id, sort_order, id);
create index if not exists idx_ds_field_service_id on ds_field (service_id, sort_order, id);
