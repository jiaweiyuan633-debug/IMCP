-- 批次 C 数据大屏增强：大屏模板表 + 3 个内置模板种子 + 模板库/设计器菜单（当前最大 menu id = 175）
CREATE TABLE screen_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT COMMENT 'NULL=内置全局模板，非空=用户自定义（租户隔离，拦截器注入）',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    code VARCHAR(64) NOT NULL COMMENT '模板编码（内置模板全局唯一）',
    category VARCHAR(32) COMMENT '分类：comprehensive/device/operation',
    theme VARCHAR(32) NOT NULL DEFAULT 'dark' COMMENT '主题',
    layout TEXT NOT NULL COMMENT '画布布局 JSON：{name,theme,widgets:[{id,type,title,dataKey,x,y,w,h}]}',
    remark VARCHAR(255),
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '1=内置模板（不可删除/直接覆盖，用作底稿另存）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_screen_tpl_tenant (tenant_id),
    UNIQUE KEY uk_screen_tpl_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据大屏模板';

-- 内置模板 1：综合态势大屏
INSERT INTO screen_template (tenant_id, name, code, category, theme, builtin, layout) VALUES
(NULL, '综合态势', 'comprehensive', 'comprehensive', 'dark', 1, '{"name":"综合态势","theme":"dark","widgets":[{"id":"m1","type":"metric","title":"登录成功","dataKey":"loginSuccessCount","x":0,"y":0,"w":3,"h":2},{"id":"m2","type":"metric","title":"操作总量","dataKey":"operTotal","x":3,"y":0,"w":3,"h":2},{"id":"m3","type":"metric","title":"操作失败","dataKey":"operErrorCount","x":6,"y":0,"w":3,"h":2},{"id":"m4","type":"metric","title":"AI 任务","dataKey":"aiTaskCount","x":9,"y":0,"w":3,"h":2},{"id":"l1","type":"line","title":"登录趋势","dataKey":"loginTrend","x":0,"y":2,"w":4,"h":4},{"id":"l2","type":"line","title":"操作趋势","dataKey":"operTrend","x":4,"y":2,"w":4,"h":4},{"id":"p1","type":"pie","title":"模块分布","dataKey":"operByModule","x":8,"y":2,"w":4,"h":4},{"id":"p2","type":"pie","title":"设备类型","dataKey":"deviceByType","x":0,"y":6,"w":4,"h":4},{"id":"p3","type":"pie","title":"设备状态","dataKey":"deviceByStatus","x":4,"y":6,"w":4,"h":4},{"id":"p4","type":"pie","title":"任务状态","dataKey":"jobByStatus","x":8,"y":6,"w":4,"h":4},{"id":"t1","type":"table","title":"最近操作","dataKey":"recentOpers","x":0,"y":10,"w":8,"h":4},{"id":"p5","type":"pie","title":"AI 状态","dataKey":"aiByStatus","x":8,"y":10,"w":4,"h":4}]}'),
(NULL, '设备监控', 'device-monitor', 'device', 'dark', 1, '{"name":"设备监控","theme":"dark","widgets":[{"id":"n1","type":"number","title":"操作总量","dataKey":"operTotal","x":0,"y":0,"w":4,"h":2},{"id":"n2","type":"number","title":"登录成功","dataKey":"loginSuccessCount","x":4,"y":0,"w":4,"h":2},{"id":"n3","type":"number","title":"AI 任务","dataKey":"aiTaskCount","x":8,"y":0,"w":4,"h":2},{"id":"p1","type":"pie","title":"设备类型","dataKey":"deviceByType","x":0,"y":2,"w":6,"h":6},{"id":"p2","type":"pie","title":"设备状态","dataKey":"deviceByStatus","x":6,"y":2,"w":6,"h":6},{"id":"l1","type":"line","title":"操作趋势","dataKey":"operTrend","x":0,"y":8,"w":6,"h":4},{"id":"t1","type":"table","title":"最近操作","dataKey":"recentOpers","x":6,"y":8,"w":6,"h":4}]}'),
(NULL, '运营分析', 'operation-analysis', 'operation', 'dark', 1, '{"name":"运营分析","theme":"dark","widgets":[{"id":"m1","type":"metric","title":"登录成功","dataKey":"loginSuccessCount","x":0,"y":0,"w":3,"h":2},{"id":"m2","type":"metric","title":"操作总量","dataKey":"operTotal","x":3,"y":0,"w":3,"h":2},{"id":"m3","type":"metric","title":"操作失败","dataKey":"operErrorCount","x":6,"y":0,"w":3,"h":2},{"id":"m4","type":"metric","title":"AI 任务","dataKey":"aiTaskCount","x":9,"y":0,"w":3,"h":2},{"id":"l1","type":"line","title":"操作趋势","dataKey":"operTrend","x":0,"y":2,"w":8,"h":4},{"id":"p1","type":"pie","title":"模块分布","dataKey":"operByModule","x":8,"y":2,"w":4,"h":4},{"id":"p2","type":"pie","title":"任务状态","dataKey":"jobByStatus","x":0,"y":6,"w":4,"h":4},{"id":"p3","type":"pie","title":"AI 状态","dataKey":"aiByStatus","x":4,"y":6,"w":4,"h":4},{"id":"l2","type":"line","title":"登录趋势","dataKey":"loginTrend","x":8,"y":6,"w":4,"h":4},{"id":"t1","type":"table","title":"最近操作","dataKey":"recentOpers","x":0,"y":10,"w":12,"h":4}]}');

-- 模板库 / 设计器菜单 + 按钮权限（当前最大 menu id = 175）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (176, 92, '大屏模板库', 'menu', 'screen-templates', 'report/screen/templates', 'report:screen:template:list', 'AppstoreOutlined', 3, 1, 1),
    (177, 92, '大屏设计器', 'menu', 'screen-designer', 'report/screen/designer', 'report:screen:designer:list', 'EditOutlined', 4, 1, 1),
    (178, 176, '保存模板', 'button', NULL, NULL, 'report:screen:template:add', NULL, 1, 0, 1),
    (179, 176, '编辑模板', 'button', NULL, NULL, 'report:screen:template:edit', NULL, 2, 0, 1),
    (180, 176, '删除模板', 'button', NULL, NULL, 'report:screen:template:delete', NULL, 3, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 176 AND 180;

-- API 资源级权限：模板写接口（V45 范式，精确路径在前、通配在后）
INSERT INTO sys_api_perm (method, path_pattern, perm_code, enabled, remark) VALUES
    ('POST',   '/api/report/screen/template',    'report:screen:template:add',    1, '大屏模板新增'),
    ('PUT',    '/api/report/screen/template',    'report:screen:template:edit',   1, '大屏模板编辑'),
    ('DELETE', '/api/report/screen/template/**', 'report:screen:template:delete', 1, '大屏模板删除');
