-- 报表模块：报表中心 + 数据大屏菜单（id 92~94，当前最大 menu id = 91）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (92, 0, '报表中心', 'dir', '/report', NULL, NULL, 'BarChartOutlined', 7, 1, 1),
    (93, 92, '报表中心', 'menu', 'center', 'report/center', 'report:center:view', 'BarChartOutlined', 1, 1, 1),
    (94, 92, '数据大屏', 'menu', 'screen', 'report/screen', 'report:screen:view', 'FundViewOutlined', 2, 1, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 92 AND 94;
