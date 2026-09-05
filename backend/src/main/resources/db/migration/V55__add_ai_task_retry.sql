-- R4-1.25：AI 任务重试权限种子。
-- Controller @PreAuthorize 已引用 ai:task:retry，但 sys_menu 无对应种子 → 角色无该权限 → 403。
-- 权限来源 selectPermsByUserId = sys_menu.perm（经 sys_role_menu），故新增按钮须显式授权 role_id=1。
-- 当前最大 menu id = 180（V53），AI 任务按钮父菜单 id = 13（create=1 / cancel=2，retry=3）。
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES (181, 13, '重试任务', 'button', NULL, NULL, 'ai:task:retry', NULL, 3, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 181;
