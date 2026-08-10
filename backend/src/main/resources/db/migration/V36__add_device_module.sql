-- 设备管理模块：设备表 + 菜单（设备管理 dir → 设备列表 menu + 按钮权限），授权给超管 role_id=1
CREATE TABLE sys_device (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    device_code VARCHAR(50) NOT NULL COMMENT '设备编码',
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_type VARCHAR(50) COMMENT '设备类型',
    location VARCHAR(200) COMMENT '安装位置',
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    description VARCHAR(255) COMMENT '设备描述',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_device_tenant_code (tenant_id, device_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备管理';

-- 顶级目录 + 子菜单 + 按钮权限（显式 id 86~90，当前最大 menu id = 85）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (86, 0, '设备管理', 'dir', '/device', NULL, NULL, 'DesktopOutlined', 6, 1, 1),
    (87, 86, '设备列表', 'menu', 'list', 'device/list', 'device:device:list', 'DesktopOutlined', 1, 1, 1),
    (88, 87, '新增设备', 'button', NULL, NULL, 'device:device:add', NULL, 1, 0, 1),
    (89, 87, '编辑设备', 'button', NULL, NULL, 'device:device:edit', NULL, 2, 0, 1),
    (90, 87, '修改状态', 'button', NULL, NULL, 'device:device:status', NULL, 3, 0, 1),
    (91, 87, '删除设备', 'button', NULL, NULL, 'device:device:delete', NULL, 4, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 86 AND 91;
