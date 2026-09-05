-- 报表定义化：报表定义表 + 菜单（parent=92 报表中心）+ 按钮权限 + API 资源级权限，授权超管 role_id=1
-- 菜单 id 143~146（当前最大 menu id = 142）
CREATE TABLE report_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(100) NOT NULL COMMENT '报表名称',
    code VARCHAR(64) NOT NULL COMMENT '报表编码',
    category VARCHAR(64) COMMENT '报表分类',
    data_source TEXT COMMENT '只读查询 SQL（含 :param 命名占位）',
    chart_type VARCHAR(32) COMMENT '图表类型 bar/line/pie/table',
    params_json TEXT COMMENT '参数定义 JSON：[{key,label,type}]',
    remark VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_report_def_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报表定义';

-- 报表中心 dir(id=92) 下新增「报表定义」menu + 新增/编辑/删除 button（path definition，component report/definition）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (143, 92, '报表定义', 'menu', 'definition', 'report/definition', 'report:definition:view', 'TableOutlined', 3, 1, 1),
    (144, 143, '新增报表', 'button', NULL, NULL, 'report:definition:add', NULL, 1, 0, 1),
    (145, 143, '编辑报表', 'button', NULL, NULL, 'report:definition:edit', NULL, 2, 0, 1),
    (146, 143, '删除报表', 'button', NULL, NULL, 'report:definition:delete', NULL, 3, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 143 AND 146;

-- API 资源级权限：报表定义写接口 → 对应权限编码（V45 范式）。
-- 精确路径在前（新增/编辑），/** 通配在后（执行/删除），ApiPermRegistry 按插入序取首个命中。
INSERT INTO sys_api_perm (method, path_pattern, perm_code, enabled, remark) VALUES
    ('POST',   '/api/report/definition',    'report:definition:add',     1, '报表定义新增'),
    ('POST',   '/api/report/definition/**', 'report:definition:execute', 1, '报表定义执行'),
    ('PUT',    '/api/report/definition',    'report:definition:edit',    1, '报表定义编辑'),
    ('DELETE', '/api/report/definition/**', 'report:definition:delete',  1, '报表定义删除');
