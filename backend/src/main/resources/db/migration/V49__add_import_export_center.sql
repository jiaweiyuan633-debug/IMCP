-- 批次4 块三：导入导出中心。
-- import_export_template（乐观锁 + 逻辑删除，code 按租户唯一）；import_export_job（导入/导出异步任务状态机）。
-- 菜单 id 153~159（当前最大 menu id = 152），顶级 dir /import-export，授权超管 role_id=1。
CREATE TABLE import_export_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    code VARCHAR(64) NOT NULL COMMENT '模板编码',
    type VARCHAR(16) NOT NULL COMMENT 'import/export',
    entity_key VARCHAR(64) NOT NULL COMMENT '目标实体标识（handler 路由）',
    config_json JSON COMMENT '列映射配置：{columns:[{key,header,required,dataType}],sheetName}',
    remark VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_imp_exp_tpl_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入导出模板';

CREATE TABLE import_export_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    template_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL COMMENT 'import/export',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCEEDED/FAILED',
    file_id BIGINT COMMENT '导入源文件 / 导出结果文件（sys_file.id）',
    file_name VARCHAR(255) COMMENT '源文件名',
    result_file_id BIGINT COMMENT '导出结果文件 id',
    total INT NOT NULL DEFAULT 0,
    success INT NOT NULL DEFAULT 0,
    failed INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入导出任务';

-- 顶级目录 + 子菜单 + 按钮权限（显式 id 153~159）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (153, 0, '导入导出中心', 'dir', '/import-export', NULL, NULL, 'SwapOutlined', 10, 1, 1),
    (154, 153, '模板管理', 'menu', 'template', 'import-export/template', 'importexport:template:list', 'ProfileOutlined', 1, 1, 1),
    (155, 154, '新增模板', 'button', NULL, NULL, 'importexport:template:add', NULL, 1, 0, 1),
    (156, 154, '编辑模板', 'button', NULL, NULL, 'importexport:template:edit', NULL, 2, 0, 1),
    (157, 154, '删除模板', 'button', NULL, NULL, 'importexport:template:delete', NULL, 3, 0, 1),
    (158, 153, '任务记录', 'menu', 'job', 'import-export/job', 'importexport:job:list', 'FileSearchOutlined', 2, 1, 1),
    (159, 158, '创建任务', 'button', NULL, NULL, 'importexport:job:create', NULL, 1, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 153 AND 159;

-- API 资源级权限（V45 范式）：写接口 URL → 所需权限编码
INSERT INTO sys_api_perm (method, path_pattern, perm_code, enabled, remark) VALUES
    ('POST',   '/api/import-export/template**', 'importexport:template:add',    1, '导入导出模板新增'),
    ('PUT',    '/api/import-export/template**', 'importexport:template:edit',   1, '导入导出模板编辑'),
    ('DELETE', '/api/import-export/template**', 'importexport:template:delete', 1, '导入导出模板删除'),
    ('POST',   '/api/import-export/job/import', 'importexport:job:create',      1, '创建导入任务'),
    ('POST',   '/api/import-export/job/export', 'importexport:job:create',      1, '创建导出任务');
