INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (76, 3, '文件管理', 'menu', 'file', 'system/file', 'system:file:list', 'FolderOutlined', 11, 1, 1),
    (77, 76, '删除文件', 'button', NULL, NULL, 'system:file:delete', NULL, 1, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 76 AND 77;
