insert into ds_dialect_rule (
    connection_id, db_type, rule_code, rule_type, rule_config, status, remark
)
select null,
       'POSTGRESQL',
       'STRING_CONCAT',
       'FUNCTION_MAPPING',
       '{"source":"concat","target":"concat","notes":"Use PostgreSQL concat function"}',
       'ENABLED',
       'Default PostgreSQL string concat rule'
where not exists (
    select 1
    from ds_dialect_rule
    where connection_id is null
      and db_type = 'POSTGRESQL'
      and rule_code = 'STRING_CONCAT'
);

insert into ds_dialect_rule (
    connection_id, db_type, rule_code, rule_type, rule_config, status, remark
)
select null,
       'MYSQL',
       'STRING_CONCAT',
       'FUNCTION_MAPPING',
       '{"source":"concat","target":"concat","notes":"Use MySQL concat function"}',
       'ENABLED',
       'Default MySQL string concat rule'
where not exists (
    select 1
    from ds_dialect_rule
    where connection_id is null
      and db_type = 'MYSQL'
      and rule_code = 'STRING_CONCAT'
);

insert into ds_dialect_rule (
    connection_id, db_type, rule_code, rule_type, rule_config, status, remark
)
select null,
       'ORACLE',
       'STRING_CONCAT',
       'FUNCTION_MAPPING',
       '{"source":"concat","target":"||","notes":"Rewrite concat to Oracle concatenation operator when needed"}',
       'ENABLED',
       'Default Oracle string concat rule'
where not exists (
    select 1
    from ds_dialect_rule
    where connection_id is null
      and db_type = 'ORACLE'
      and rule_code = 'STRING_CONCAT'
);

insert into ds_dialect_rule (
    connection_id, db_type, rule_code, rule_type, rule_config, status, remark
)
select null,
       'POSTGRESQL',
       'CURRENT_TIMESTAMP',
       'FUNCTION_MAPPING',
       '{"source":"current_timestamp","target":"current_timestamp","notes":"Use PostgreSQL current_timestamp"}',
       'ENABLED',
       'Default PostgreSQL current timestamp rule'
where not exists (
    select 1
    from ds_dialect_rule
    where connection_id is null
      and db_type = 'POSTGRESQL'
      and rule_code = 'CURRENT_TIMESTAMP'
);

insert into ds_dialect_rule (
    connection_id, db_type, rule_code, rule_type, rule_config, status, remark
)
select null,
       'MYSQL',
       'CURRENT_TIMESTAMP',
       'FUNCTION_MAPPING',
       '{"source":"current_timestamp","target":"current_timestamp","notes":"Use MySQL current_timestamp"}',
       'ENABLED',
       'Default MySQL current timestamp rule'
where not exists (
    select 1
    from ds_dialect_rule
    where connection_id is null
      and db_type = 'MYSQL'
      and rule_code = 'CURRENT_TIMESTAMP'
);

insert into ds_dialect_rule (
    connection_id, db_type, rule_code, rule_type, rule_config, status, remark
)
select null,
       'ORACLE',
       'CURRENT_TIMESTAMP',
       'FUNCTION_MAPPING',
       '{"source":"current_timestamp","target":"current_timestamp","notes":"Use Oracle current_timestamp"}',
       'ENABLED',
       'Default Oracle current timestamp rule'
where not exists (
    select 1
    from ds_dialect_rule
    where connection_id is null
      and db_type = 'ORACLE'
      and rule_code = 'CURRENT_TIMESTAMP'
);
