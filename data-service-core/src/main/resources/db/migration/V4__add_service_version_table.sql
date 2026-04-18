create table if not exists ds_service_version (
    id bigserial primary key,
    service_id bigint not null references ds_service (id),
    version integer not null,
    status varchar(32) not null,
    service_definition_json text not null,
    source_definition_json text not null,
    param_definition_json text not null,
    field_definition_json text not null,
    sql_definition_json text not null,
    created_at timestamp not null default current_timestamp,
    created_by varchar(64) not null default 'SYSTEM',
    constraint uk_ds_service_version unique (service_id, version)
);

create index if not exists idx_ds_service_version_service_id on ds_service_version (service_id, version desc, id desc);
