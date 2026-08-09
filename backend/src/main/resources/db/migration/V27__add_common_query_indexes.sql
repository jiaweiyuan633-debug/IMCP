ALTER TABLE sys_login_log
    ADD KEY idx_sys_login_log_tenant_username (tenant_id, username);

ALTER TABLE sys_oper_log
    ADD KEY idx_sys_oper_log_tenant_user (tenant_id, user_id);

ALTER TABLE sys_workflow
    ADD KEY idx_sys_workflow_tenant_status (tenant_id, status);

ALTER TABLE ai_task
    ADD KEY idx_ai_task_tenant_status (tenant_id, status);

ALTER TABLE sys_notice
    ADD KEY idx_sys_notice_tenant_status (tenant_id, status);

ALTER TABLE sys_job
    ADD KEY idx_sys_job_tenant_status (tenant_id, status);
