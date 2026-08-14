-- 修正：共享字典菜单 140 原挂在 35（menu 类型「字典管理」）下。
-- 前端 buildDynamicRouteChildren 只对 dir 类型递归 children，menu 类型的 children 不会生成路由，
-- 导致 /system/shared-dict 路由永不注册 → 前端 404。
-- 将 140 改挂到 3（dir 类型「系统管理」/system）下，与字典管理平级，路由即可正常注册。
-- 按钮 141/142 保持 parent=140 不变。
UPDATE sys_menu SET parent_id = 3, sort = 12 WHERE id = 140;
