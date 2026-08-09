-- 用户名唯一键按租户隔离，支持多租户下同名用户
-- 既有全局唯一约束在跨租户场景会误伤（租户 A 占用用户名则租户 B 无法使用同名账号）
-- 与 V12/V17 中角色/岗位/字典/参数的 (tenant_id, code) 模式保持一致
ALTER TABLE sys_user
    DROP INDEX uk_sys_user_username,
    ADD UNIQUE KEY uk_sys_user_tenant_username (tenant_id, username);
