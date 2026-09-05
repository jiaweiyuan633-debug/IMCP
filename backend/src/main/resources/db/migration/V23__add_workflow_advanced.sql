ALTER TABLE sys_process_node
    ADD COLUMN node_type VARCHAR(20) NOT NULL DEFAULT 'APPROVE' AFTER node_key,
    ADD COLUMN condition_expression VARCHAR(500) NULL AFTER node_type,
    ADD COLUMN timeout_hours INT NOT NULL DEFAULT 48 AFTER condition_expression;

ALTER TABLE sys_workflow
    ADD COLUMN form_data TEXT NULL AFTER content,
    ADD COLUMN current_node_ids VARCHAR(255) NULL AFTER current_node_name,
    ADD COLUMN current_node_assigned_at DATETIME NULL AFTER current_node_ids,
    ADD COLUMN timeout_notified TINYINT NOT NULL DEFAULT 0 AFTER current_node_assigned_at;
