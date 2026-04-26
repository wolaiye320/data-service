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
    constraint uk_ds_connection_code unique (connection_code),
    constraint chk_ds_connection_status check (status in ('ENABLED', 'DISABLED'))
);

create table if not exists ds_service (
    id bigserial primary key,
    service_code varchar(64) not null,
    service_name varchar(128) not null,
    sql_type varchar(32) not null default 'SIMPLE_SQL',
    status varchar(32) not null default 'DRAFT',
    current_version integer,
    max_batch_size integer,
    max_result_rows integer,
    query_timeout_seconds integer,
    federated_query_timeout_seconds integer,
    tenant_id varchar(64),
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint chk_ds_service_status check (status in ('DRAFT', 'PUBLISHED', 'DISABLED'))
);

create unique index if not exists uk_ds_service_service_code_active
    on ds_service (service_code)
    where deleted = false;

create table if not exists ds_service_version (
    id bigserial primary key,
    service_id bigint not null references ds_service (id),
    version integer not null,
    status varchar(32) not null,
    sql_type varchar(32) not null default 'SIMPLE_SQL',
    sql_text text not null,
    source_snapshot_json text not null default '[]',
    param_snapshot_json text not null default '[]',
    field_snapshot_json text not null default '[]',
    validation_snapshot_json text,
    plan_snapshot_json text,
    request_context_snapshot_json text,
    published_at timestamp,
    published_by varchar(64),
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_service_version unique (service_id, version),
    constraint chk_ds_service_version_status check (status in ('DRAFT', 'PUBLISHED', 'DISABLED')),
    constraint chk_ds_service_version_sql_type check (sql_type in ('SIMPLE_SQL', 'FEDERATED_SQL'))
);

create table if not exists ds_cache_policy (
    id bigserial primary key,
    service_id bigint not null references ds_service (id),
    enabled boolean not null default false,
    ttl_seconds integer not null default 300,
    cache_key_template varchar(512) not null default 'data-service:{serviceCode}:v{version}:{paramHash}',
    max_entries integer not null default 1000,
    context_keys_json text not null default '[]',
    remark varchar(512),
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    updated_at timestamp not null default current_timestamp,
    updated_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_cache_policy_service unique (service_id)
);

create table if not exists ds_source_capability (
    id bigserial primary key,
    connection_id bigint not null references ds_connection (id),
    db_type varchar(32) not null,
    capability_code varchar(64) not null,
    capability_value varchar(256) not null,
    capability_detail_json text,
    scope varchar(32) not null default 'GLOBAL',
    scope_value varchar(128),
    status varchar(32) not null default 'ENABLED',
    remark varchar(512),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint chk_ds_source_capability_status check (status in ('ENABLED', 'DISABLED'))
);

create unique index if not exists uk_ds_source_capability_connection_scope
    on ds_source_capability (connection_id, capability_code, scope, coalesce(scope_value, ''));

create table if not exists ds_dialect_rule (
    id bigserial primary key,
    connection_id bigint references ds_connection (id),
    db_type varchar(32),
    rule_code varchar(64) not null,
    rule_type varchar(32) not null,
    rule_config text not null,
    status varchar(32) not null default 'ENABLED',
    remark varchar(512),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint chk_ds_dialect_rule_status check (status in ('ENABLED', 'DISABLED')),
    constraint chk_ds_dialect_rule_scope check (connection_id is not null or db_type is not null)
);

create table if not exists ds_audit_log (
    id bigserial primary key,
    service_id bigint references ds_service (id),
    connection_id bigint references ds_connection (id),
    event_type varchar(64) not null,
    target_type varchar(32) not null,
    target_id varchar(64) not null,
    operator varchar(64) not null,
    operator_role varchar(32) not null,
    operation_result varchar(16) not null,
    trace_id varchar(64),
    request_ip varchar(64),
    change_summary varchar(1024) not null,
    detail_json text,
    context_summary_json text,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    constraint chk_ds_audit_log_operation_result check (operation_result in ('SUCCESS', 'FAILURE'))
);

create index if not exists idx_ds_connection_status_created
    on ds_connection (status, created_at desc, id desc);

create index if not exists idx_ds_service_status_created
    on ds_service (status, created_at desc, id desc)
    where deleted = false;

create index if not exists idx_ds_service_current_version
    on ds_service (current_version)
    where deleted = false;

create index if not exists idx_ds_service_version_service_id
    on ds_service_version (service_id, version desc, id desc);

create index if not exists idx_ds_service_version_status_created
    on ds_service_version (service_id, status, version desc, id desc);

create index if not exists idx_ds_cache_policy_service_enabled
    on ds_cache_policy (service_id, enabled)
    where deleted = false;

create index if not exists idx_ds_source_capability_connection_code
    on ds_source_capability (connection_id, capability_code);

create index if not exists idx_ds_source_capability_connection_status
    on ds_source_capability (connection_id, status, capability_code, created_at desc);

create index if not exists idx_ds_dialect_rule_connection_status
    on ds_dialect_rule (connection_id, status, rule_code, created_at desc);

create index if not exists idx_ds_dialect_rule_db_type_status
    on ds_dialect_rule (db_type, status, rule_code, created_at desc);

create index if not exists idx_ds_audit_log_service_created
    on ds_audit_log (service_id, created_at desc, id desc);

create index if not exists idx_ds_audit_log_connection_created
    on ds_audit_log (connection_id, created_at desc, id desc);

create index if not exists idx_ds_audit_log_operator_created
    on ds_audit_log (operator, created_at desc, id desc);

create index if not exists idx_ds_audit_log_event_created
    on ds_audit_log (event_type, created_at desc, id desc);

create index if not exists idx_ds_audit_log_result_created
    on ds_audit_log (operation_result, created_at desc, id desc);

create index if not exists idx_ds_audit_log_trace_id
    on ds_audit_log (trace_id);
