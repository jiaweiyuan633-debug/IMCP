-- 批次4 块二：设备物模型 + 遥测。
-- 物模型表 device_thing_model（乐观锁 + 逻辑删除，device_type 按租户唯一）；遥测表 device_telemetry（纯追加时序，无 version/deleted）。
-- 菜单 id 147~152（当前最大 menu id = 146），parent=86 设备管理 dir，授权超管 role_id=1。
CREATE TABLE device_thing_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    device_type VARCHAR(64) NOT NULL COMMENT '物模型类型编码',
    name VARCHAR(100) NOT NULL COMMENT '物模型名称',
    description VARCHAR(255),
    properties_json JSON COMMENT '属性定义：[{key,name,dataType,unit,mode(ro/rw)}]',
    events_json JSON COMMENT '事件定义：[{key,name,params:[{key,name,dataType}]}]',
    services_json JSON COMMENT '服务定义：[{key,name,params:[{key,name,dataType}],output:{...}}]',
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, updated_by BIGINT,
    version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_thing_model_tenant_type (tenant_id, device_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备物模型';

CREATE TABLE device_telemetry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    device_id BIGINT NOT NULL COMMENT '设备 id（sys_device.id）',
    property_key VARCHAR(64) NOT NULL COMMENT '属性标识',
    value_num DECIMAL(24,8) NULL COMMENT '数值',
    value_text VARCHAR(255) NULL COMMENT '文本/枚举',
    occurred_at DATETIME NOT NULL COMMENT '采集时间（设备时钟，非入库时间）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_telemetry_dev_prop_ts (device_id, property_key, occurred_at),
    KEY idx_telemetry_tenant_ts (tenant_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备遥测数据';

-- 菜单：147 物模型(menu) + 148~150 新增/编辑/删除 button；151 设备遥测(menu) + 152 上报 button
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (147, 86, '物模型', 'menu', 'thing-model', 'device/thing-model', 'device:thing-model:list', 'ApartmentOutlined', 2, 1, 1),
    (148, 147, '新增物模型', 'button', NULL, NULL, 'device:thing-model:add', NULL, 1, 0, 1),
    (149, 147, '编辑物模型', 'button', NULL, NULL, 'device:thing-model:edit', NULL, 2, 0, 1),
    (150, 147, '删除物模型', 'button', NULL, NULL, 'device:thing-model:delete', NULL, 3, 0, 1),
    (151, 86, '设备遥测', 'menu', 'telemetry', 'device/telemetry', 'device:telemetry:list', 'FieldTimeOutlined', 3, 1, 1),
    (152, 151, '上报遥测', 'button', NULL, NULL, 'device:telemetry:report', NULL, 1, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 147 AND 152;

-- API 资源级权限（V45 范式）：写接口 URL → 所需权限编码
INSERT INTO sys_api_perm (method, path_pattern, perm_code, enabled, remark) VALUES
    ('POST',   '/api/device/thing-model/**',   'device:thing-model:add',    1, '新增物模型'),
    ('PUT',    '/api/device/thing-model/**',   'device:thing-model:edit',   1, '编辑物模型'),
    ('DELETE', '/api/device/thing-model/**',   'device:thing-model:delete', 1, '删除物模型'),
    ('POST',   '/api/device/telemetry/report', 'device:telemetry:report',   1, '上报遥测');
