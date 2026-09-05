INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (16, 4, '新增用户', 'button', NULL, NULL, 'system:user:add', NULL, 1, 0, 1),
    (17, 4, '编辑用户', 'button', NULL, NULL, 'system:user:edit', NULL, 2, 0, 1),
    (18, 4, '删除用户', 'button', NULL, NULL, 'system:user:delete', NULL, 3, 0, 1),
    (19, 4, '修改用户状态', 'button', NULL, NULL, 'system:user:status', NULL, 4, 0, 1),
    (20, 4, '分配角色', 'button', NULL, NULL, 'system:user:role', NULL, 5, 0, 1),
    (21, 5, '新增角色', 'button', NULL, NULL, 'system:role:add', NULL, 1, 0, 1),
    (22, 5, '编辑角色', 'button', NULL, NULL, 'system:role:edit', NULL, 2, 0, 1),
    (23, 5, '删除角色', 'button', NULL, NULL, 'system:role:delete', NULL, 3, 0, 1),
    (24, 5, '分配菜单', 'button', NULL, NULL, 'system:role:assign', NULL, 4, 0, 1),
    (25, 6, '新增菜单', 'button', NULL, NULL, 'system:menu:add', NULL, 1, 0, 1),
    (26, 6, '编辑菜单', 'button', NULL, NULL, 'system:menu:edit', NULL, 2, 0, 1),
    (27, 6, '删除菜单', 'button', NULL, NULL, 'system:menu:delete', NULL, 3, 0, 1),
    (28, 10, '强制下线', 'button', NULL, NULL, 'monitor:online:kick', NULL, 1, 0, 1),
    (29, 11, '清理缓存', 'button', NULL, NULL, 'monitor:cache:delete', NULL, 1, 0, 1),
    (30, 13, '创建任务', 'button', NULL, NULL, 'ai:task:create', NULL, 1, 0, 1),
    (31, 13, '取消任务', 'button', NULL, NULL, 'ai:task:cancel', NULL, 2, 0, 1),
    (32, 14, '编辑配置', 'button', NULL, NULL, 'ai:config:edit', NULL, 1, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 16 AND 32;

