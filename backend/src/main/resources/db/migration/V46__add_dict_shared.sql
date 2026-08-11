-- 字典租户深化：共享字典（tenant_id=0 全局一份）+ 租户覆盖模型 + 租户粒度缓存失效。菜单 id 140~142（当前最大 menu id = 139）
ALTER TABLE sys_dict_type ADD COLUMN is_shared TINYINT NOT NULL DEFAULT 0 COMMENT '是否共享字典 1共享(tenant_id=0 全局一份) 0租户私有';

-- 种子共享字典：tenant_id=0，所有租户可读；租户可建同 dict_type 数据覆盖同名 dict_value（UK: tenant_id+dict_type+dict_value）
INSERT INTO sys_dict_type (tenant_id, dict_name, dict_type, status, is_shared, remark)
VALUES (0, '通用启用状态', 'common_status', 1, 1, '共享字典示例：租户可复用并覆盖同名键值');

INSERT INTO sys_dict_data (tenant_id, dict_type, dict_label, dict_value, dict_sort, list_class, is_default, status, remark)
VALUES
    (0, 'common_status', '启用', '1', 1, 'primary', 1, 1, '共享默认'),
    (0, 'common_status', '停用', '0', 2, 'danger', 0, 1, '共享默认');

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (140, 35, '共享字典', 'menu', 'shared-dict', 'system/dict/shared', 'system:dict:shared:list', 'GlobalOutlined', 7, 1, 1),
    (141, 140, '新增共享', 'button', NULL, NULL, 'system:dict:shared:add', NULL, 1, 0, 1),
    (142, 140, '编辑共享', 'button', NULL, NULL, 'system:dict:shared:edit', NULL, 2, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 140 AND 142;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id = 140;
