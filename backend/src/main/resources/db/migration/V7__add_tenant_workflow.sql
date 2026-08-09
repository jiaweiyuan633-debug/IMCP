CREATE TABLE sys_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_name VARCHAR(100) NOT NULL,
    tenant_code VARCHAR(50) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='tenant';

CREATE TABLE sys_workflow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    process_name VARCHAR(100) NOT NULL,
    biz_type VARCHAR(50) NOT NULL DEFAULT 'demo',
    biz_id BIGINT,
    applicant_id BIGINT,
    applicant_name VARCHAR(50),
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='simple workflow';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (64, 3, '租户管理', 'menu', 'tenant', 'system/tenant', 'system:tenant:list', 'ClusterOutlined', 9, 1, 1),
    (65, 3, '工作流', 'menu', 'workflow', 'system/workflow', 'system:workflow:list', 'DeploymentUnitOutlined', 10, 1, 1),
    (66, 64, '新增租户', 'button', NULL, NULL, 'system:tenant:add', NULL, 1, 0, 1),
    (67, 64, '编辑租户', 'button', NULL, NULL, 'system:tenant:edit', NULL, 2, 0, 1),
    (68, 64, '删除租户', 'button', NULL, NULL, 'system:tenant:delete', NULL, 3, 0, 1),
    (69, 65, '审批通过', 'button', NULL, NULL, 'system:workflow:approve', NULL, 1, 0, 1),
    (70, 65, '审批拒绝', 'button', NULL, NULL, 'system:workflow:reject', NULL, 2, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 64 AND 70;

INSERT INTO sys_tenant (tenant_name, tenant_code, status, contact_name, contact_phone)
VALUES ('默认租户', 'default', 1, '系统管理员', NULL);

INSERT INTO sys_workflow (process_name, biz_type, applicant_id, applicant_name, content, status)
VALUES ('示例审批', 'demo', 1, 'admin', '这是一个示例审批流程。', 'PENDING');

