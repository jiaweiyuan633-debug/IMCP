ALTER TABLE sys_tenant
    ADD COLUMN user_limit INT NOT NULL DEFAULT 100 AFTER contact_phone,
    ADD COLUMN storage_limit_mb BIGINT NOT NULL DEFAULT 1024 AFTER user_limit,
    ADD COLUMN admin_user_id BIGINT NULL AFTER storage_limit_mb;
