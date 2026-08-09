ALTER TABLE sys_workflow
    ADD COLUMN assignee_user_id BIGINT NULL AFTER current_node_name,
    ADD COLUMN assignee_name VARCHAR(50) NULL AFTER assignee_user_id;
