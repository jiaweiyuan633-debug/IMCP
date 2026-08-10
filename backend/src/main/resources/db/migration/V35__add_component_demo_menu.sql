-- 前端组件沉淀示例菜单：开发资源 → 组件示例（展示 ProTable/ProSearchForm/ModalForm/StatusTag/useTableQuery 用法）
INSERT INTO sys_menu (parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES (0, '开发资源', 'dir', 'dev', NULL, NULL, 'ExperimentOutlined', 100, 1, 1);

INSERT INTO sys_menu (parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES (84, '组件示例', 'menu', 'component-demo', 'demo/component-demo', 'demo:component:view', 'AppstoreOutlined', 1, 1, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (84, 85);
