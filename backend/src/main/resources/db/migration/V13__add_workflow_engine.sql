CREATE TABLE sys_process_def (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    def_name VARCHAR(100) NOT NULL,
    def_key VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_process_def_tenant_key (tenant_id, def_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='process definition';

CREATE TABLE sys_process_node (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    process_def_id BIGINT NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    node_key VARCHAR(100) NOT NULL,
    node_order INT NOT NULL DEFAULT 0,
    approver_role_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_process_node_def (process_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='process node';

ALTER TABLE sys_workflow
    ADD COLUMN process_def_id BIGINT NULL AFTER biz_type,
    ADD COLUMN current_node_id BIGINT NULL AFTER process_def_id,
    ADD COLUMN current_node_name VARCHAR(100) NULL AFTER current_node_id;

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (78, 65, '新增流程定义', 'button', NULL, NULL, 'system:workflow:def:add', NULL, 1, 0, 1),
    (79, 65, '编辑流程定义', 'button', NULL, NULL, 'system:workflow:def:edit', NULL, 2, 0, 1),
    (80, 65, '删除流程定义', 'button', NULL, NULL, 'system:workflow:def:delete', NULL, 3, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 78 AND 80;

INSERT INTO sys_process_def
    (tenant_id, def_name, def_key, description, status)
VALUES
    (1, '通用审批', 'general_approval', '部门负责人审批后由管理员终审', 1);

INSERT INTO sys_process_node
    (tenant_id, process_def_id, node_name, node_key, node_order, approver_role_id)
VALUES
    (1, 1, '部门审批', 'dept_approve', 1, 2),
    (1, 1, '管理员终审', 'admin_approve', 2, 1);
