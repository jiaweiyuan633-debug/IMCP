-- R4-1.36 菜单 id 动态化（批次9）
-- 背景：V1~V51 的菜单种子迁移均显式硬编码 id 并按 id 区间守卫/授权
--   （如 V51 注释「当前最大 menu id=165，新增 166~175」）。手工维护 id 区间
--   在并发插入、批次回滚后重放时易冲突/覆盖，且 id 与业务语义无绑定关系。
-- 约定：自本迁移起，sys_menu 的 id 一律由数据库自增分配；后续菜单迁移
--   以业务键 perm（唯一）定位——守卫用 WHERE perm='...'，授权用
--   INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE perm='...'。
-- 本迁移：
--   1) 建立 uk_sys_menu_perm 唯一索引，把 perm 提升为菜单业务定位键；
--   2) 建索引前清理历史重复 perm（保留最小 id 行），避免建索引失败。
--      perm 为 NULL 的目录/菜单行不受唯一索引约束（MySQL 唯一索引允许多个 NULL）。
DELETE m1 FROM sys_menu m1
JOIN sys_menu m2 ON m1.perm = m2.perm AND m1.id > m2.id
WHERE m1.perm IS NOT NULL AND m1.perm <> '';

CREATE UNIQUE INDEX uk_sys_menu_perm ON sys_menu (perm);
