alter table ds_service
    drop constraint if exists uk_ds_service_service_code;

alter table ds_service
    drop constraint if exists uk_ds_service_code;

create unique index if not exists uk_ds_service_service_code_active
    on ds_service (service_code)
    where deleted = false;
