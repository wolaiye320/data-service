create table if not exists ds_sql_text (
    id bigserial primary key,
    service_id bigint not null references ds_service (id),
    version integer not null,
    sql_text text not null,
    sql_comment varchar(512),
    status varchar(32) not null,
    is_current boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_sql_text_service_version unique (service_id, version)
);

create unique index if not exists uk_ds_sql_text_current
    on ds_sql_text (service_id)
    where is_current = true;

create table if not exists ds_sql_plan (
    id bigserial primary key,
    service_id bigint not null references ds_service (id),
    version integer not null,
    plan_stage varchar(32) not null,
    plan_format varchar(32) not null,
    plan_content text not null,
    stage_graph_json text,
    pushdown_summary text,
    fallback_reason text,
    cost_summary text,
    local_execution_summary text,
    datasource_scope varchar(256),
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM'
);

create index if not exists idx_ds_sql_plan_service_version_stage
    on ds_sql_plan (service_id, version, plan_stage);

create table if not exists ds_sql_validate_log (
    id bigserial primary key,
    service_id bigint not null references ds_service (id),
    version integer not null,
    validate_stage varchar(32) not null,
    result varchar(16) not null,
    message varchar(1024) not null,
    detail_json text,
    trace_id varchar(64),
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM'
);

create index if not exists idx_ds_sql_validate_log_service_version_stage
    on ds_sql_validate_log (service_id, version, validate_stage, created_at desc);

create table if not exists ds_source_capability (
    id bigserial primary key,
    connection_id bigint not null references ds_connection (id),
    db_type varchar(32) not null,
    capability_code varchar(64) not null,
    capability_value varchar(256) not null,
    capability_detail_json text,
    scope varchar(32) not null default 'GLOBAL',
    scope_value varchar(128),
    enabled boolean not null default true,
    remark varchar(512),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_ds_source_capability_connection_code
    on ds_source_capability (connection_id, capability_code);
