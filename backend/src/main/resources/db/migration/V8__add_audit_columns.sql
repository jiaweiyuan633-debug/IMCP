ALTER TABLE sys_role
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_dept
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_post
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_config
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_dict_type
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_dict_data
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_notice
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_job
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_tenant
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;
ALTER TABLE sys_workflow
    ADD COLUMN updated_by BIGINT NULL AFTER updated_at,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;

