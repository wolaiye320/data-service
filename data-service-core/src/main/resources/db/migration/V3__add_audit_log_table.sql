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
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM'
);

create index if not exists idx_ds_audit_log_service_created on ds_audit_log (service_id, created_at desc, id desc);
create index if not exists idx_ds_audit_log_connection_created on ds_audit_log (connection_id, created_at desc, id desc);
create index if not exists idx_ds_audit_log_operator_created on ds_audit_log (operator, created_at desc, id desc);
create index if not exists idx_ds_audit_log_event_created on ds_audit_log (event_type, created_at desc, id desc);
create index if not exists idx_ds_audit_log_result_created on ds_audit_log (operation_result, created_at desc, id desc);
create index if not exists idx_ds_audit_log_trace_id on ds_audit_log (trace_id);
