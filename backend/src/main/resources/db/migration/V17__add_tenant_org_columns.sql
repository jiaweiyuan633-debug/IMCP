ALTER TABLE sys_role ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE sys_role DROP INDEX uk_sys_role_code;
ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_tenant_code (tenant_id, code);

ALTER TABLE sys_dept ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;

ALTER TABLE sys_post ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE sys_post DROP INDEX uk_sys_post_code;
ALTER TABLE sys_post ADD UNIQUE KEY uk_sys_post_tenant_code (tenant_id, post_code);

ALTER TABLE sys_dict_type ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE sys_dict_type DROP INDEX uk_sys_dict_type;
ALTER TABLE sys_dict_type ADD UNIQUE KEY uk_sys_dict_type_tenant (tenant_id, dict_type);

ALTER TABLE sys_dict_data ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE sys_dict_data DROP INDEX uk_sys_dict_data;
ALTER TABLE sys_dict_data ADD UNIQUE KEY uk_sys_dict_data_tenant (tenant_id, dict_type, dict_value);

ALTER TABLE sys_config ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE sys_config DROP INDEX uk_sys_config_key;
ALTER TABLE sys_config ADD UNIQUE KEY uk_sys_config_key_tenant (tenant_id, config_key);
