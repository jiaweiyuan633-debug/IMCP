INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (81, 7, '审计日志', 'menu', 'audit', 'monitor/audit', 'monitor:audit:list', 'AuditOutlined', 9, 1, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 81;
