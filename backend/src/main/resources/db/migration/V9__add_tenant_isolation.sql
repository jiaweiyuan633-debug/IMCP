ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE sys_file ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;

CREATE TABLE sys_workflow_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(50),
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_workflow_log_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='workflow log';
