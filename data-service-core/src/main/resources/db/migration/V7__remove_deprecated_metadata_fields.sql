alter table ds_catalog
    drop constraint if exists uk_ds_catalog_code;

alter table ds_catalog
    drop column if exists catalog_code,
    drop column if exists catalog_name,
    drop column if exists status,
    drop column if exists remark;

create unique index if not exists uk_ds_catalog_connection_type_value_active
    on ds_catalog (connection_id, catalog_type, catalog_value)
    where deleted = false;

alter table ds_source
    drop column if exists status,
    drop column if exists remark;

alter table ds_param
    drop column if exists remark;

alter table ds_field
    drop column if exists remark;
