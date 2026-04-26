alter table ds_service
    add column if not exists default_connection_code varchar(64);

create index if not exists idx_ds_service_default_connection_code
    on ds_service (default_connection_code)
    where deleted = false;
