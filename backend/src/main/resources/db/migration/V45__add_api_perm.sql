-- API 资源级权限：URL 方法+路径 → 所需权限编码。URL 层从"仅认证"升级为"认证+资源权限"。菜单 id 135~139（当前最大 menu id = 134）
CREATE TABLE sys_api_perm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    method VARCHAR(10) NOT NULL COMMENT 'HTTP 方法 GET/POST/PUT/DELETE，* 表示任意',
    path_pattern VARCHAR(200) NOT NULL COMMENT '路径模式，支持 Ant 通配，如 /api/system/user/**',
    perm_code VARCHAR(100) NOT NULL COMMENT '访问该 URL 所需的权限编码',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 1启用 0停用',
    remark VARCHAR(255) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_api_perm (method, path_pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 资源权限映射';

-- 将已有按钮级权限沉淀为资源级示例（仅超管可全量管理，运维仅可查看）
INSERT INTO sys_api_perm (method, path_pattern, perm_code, enabled, remark) VALUES
    ('POST',   '/api/system/user/**',       'system:user:add',    1, '用户新增'),
    ('PUT',    '/api/system/user/**',       'system:user:edit',   1, '用户编辑'),
    ('DELETE', '/api/system/user/**',       'system:user:delete', 1, '用户删除'),
    ('POST',   '/api/system/role/**',       'system:role:add',    1, '角色新增'),
    ('PUT',    '/api/system/role/**',       'system:role:edit',   1, '角色编辑'),
    ('DELETE', '/api/system/role/**',       'system:role:delete', 1, '角色删除'),
    ('POST',   '/api/system/menu/**',       'system:menu:add',    1, '菜单新增'),
    ('PUT',    '/api/system/menu/**',       'system:menu:edit',   1, '菜单编辑'),
    ('DELETE', '/api/system/menu/**',       'system:menu:delete', 1, '菜单删除'),
    ('POST',   '/api/system/dict/type',     'system:dict:add',    1, '字典类型新增'),
    ('PUT',    '/api/system/dict/type',     'system:dict:edit',   1, '字典类型编辑'),
    ('DELETE', '/api/system/dict/type/**',  'system:dict:delete', 1, '字典类型删除'),
    ('POST',   '/api/system/dict/data',     'system:dict:data:add', 1, '字典数据新增'),
    ('PUT',    '/api/system/dict/data',     'system:dict:data:edit', 1, '字典数据编辑'),
    ('DELETE', '/api/system/dict/data/**',  'system:dict:data:delete', 1, '字典数据删除');

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (135, 3, '接口权限', 'menu', 'api-perm', 'system/api-perm', 'system:api-perm:list', 'SafetyOutlined', 11, 1, 1),
    (136, 135, '新增映射', 'button', NULL, NULL, 'system:api-perm:add', NULL, 1, 0, 1),
    (137, 135, '编辑映射', 'button', NULL, NULL, 'system:api-perm:edit', NULL, 2, 0, 1),
    (138, 135, '删除映射', 'button', NULL, NULL, 'system:api-perm:delete', NULL, 3, 0, 1),
    (139, 135, '重载映射', 'button', NULL, NULL, 'system:api-perm:reload', NULL, 4, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 135 AND 139;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id = 135;
