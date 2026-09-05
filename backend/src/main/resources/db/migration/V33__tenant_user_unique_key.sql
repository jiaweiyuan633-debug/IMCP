-- 用户名唯一键按租户隔离，支持多租户下同名用户
-- 既有全局唯一约束在跨租户场景会误伤（租户 A 占用用户名则租户 B 无法使用同名账号）
-- 与 V12/V17 中角色/岗位/字典/参数的 (tenant_id, code) 模式保持一致
-- 拆分为两条独立 ALTER TABLE（与 V12/V17 写法一致），组合子句写法会被 H2 等解析器的 MySQL 模式误报语法错误
ALTER TABLE sys_user
    DROP INDEX uk_sys_user_username;
ALTER TABLE sys_user
    ADD UNIQUE KEY uk_sys_user_tenant_username (tenant_id, username);
