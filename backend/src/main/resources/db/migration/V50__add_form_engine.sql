-- 低代码表单引擎：form_definition + form_instance + 菜单（表单引擎 dir → 表单定义/提交记录 menu + 按钮），授权给超管 role_id=1
-- 注意：form_definition.version 即乐观锁字段（@Version），发布时递增；form_instance 不设 version
CREATE TABLE form_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(100) NOT NULL COMMENT '表单名称',
    code VARCHAR(64) NOT NULL COMMENT '表单编码',
    description VARCHAR(255),
    schema_json JSON NOT NULL COMMENT '字段定义：[{key,label,type(输入/文本/数字/日期/下拉/多选/开关),required,options,placeholder,maxLength}]',
    layout_json JSON COMMENT '布局配置：{columns:1|2}',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
    version INT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_form_def_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='低代码表单定义';

CREATE TABLE form_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    form_id BIGINT NOT NULL,
    form_code VARCHAR(64) NOT NULL,
    data_json JSON NOT NULL COMMENT '提交数据',
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/APPROVED/REJECTED',
    submitter_id BIGINT,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_form_instance_code_tenant (tenant_id, form_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单提交记录';

-- API 资源级权限：写接口 → 对应权限编码（V45 范式，精确路径在前，/** 通配在后）
INSERT INTO sys_api_perm (method, path_pattern, perm_code, enabled, remark) VALUES
    ('POST',   '/api/form/definition',           'form:definition:add',     1, '表单新增'),
    ('PUT',    '/api/form/definition',           'form:definition:edit',    1, '表单编辑'),
    ('PUT',    '/api/form/definition/*/publish', 'form:definition:publish', 1, '表单发布'),
    ('DELETE', '/api/form/definition/**',        'form:definition:delete',  1, '表单删除'),
    ('POST',   '/api/form/instance/submit',      'form:instance:submit',    1, '表单提交'),
    ('PUT',    '/api/form/instance/*/status',    'form:instance:approve',   1, '提交审批');

-- 顶级目录 + 子菜单 + 按钮权限（显式 id 160~165，当前最大 menu id = 159）
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (160, 0, '表单引擎', 'dir', '/form', NULL, NULL, 'FormOutlined', 11, 1, 1),
    (161, 160, '表单定义', 'menu', 'definition', 'form/definition', 'form:definition:list', 'FormOutlined', 1, 1, 1),
    (162, 161, '新增定义', 'button', NULL, NULL, 'form:definition:add', NULL, 1, 0, 1),
    (163, 161, '编辑定义', 'button', NULL, NULL, 'form:definition:edit', NULL, 2, 0, 1),
    (164, 161, '删除定义', 'button', NULL, NULL, 'form:definition:delete', NULL, 3, 0, 1),
    (165, 160, '提交记录', 'menu', 'instance', 'form/instance', 'form:instance:list', 'FileTextOutlined', 2, 1, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 160 AND 165;
