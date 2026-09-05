ALTER TABLE sys_role ADD COLUMN data_scope TINYINT NOT NULL DEFAULT 1 AFTER status;

CREATE TABLE sys_role_dept (
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role dept';

CREATE TABLE sys_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_name VARCHAR(100) NOT NULL,
    job_group VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
    invoke_target VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    misfire_policy VARCHAR(20) NOT NULL DEFAULT '1',
    concurrent TINYINT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sys_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='scheduled job';

CREATE TABLE sys_job_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_name VARCHAR(100) NOT NULL,
    job_group VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
    invoke_target VARCHAR(200),
    job_message VARCHAR(500),
    status TINYINT NOT NULL DEFAULT 1,
    exception_info TEXT,
    start_time DATETIME,
    end_time DATETIME,
    KEY idx_sys_job_log_job_name (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='job log';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (52, 7, '定时任务', 'menu', 'job', 'monitor/job', 'monitor:job:list', 'FieldTimeOutlined', 5, 1, 1),
    (53, 52, '新增任务', 'button', NULL, NULL, 'monitor:job:add', NULL, 1, 0, 1),
    (54, 52, '编辑任务', 'button', NULL, NULL, 'monitor:job:edit', NULL, 2, 0, 1),
    (55, 52, '删除任务', 'button', NULL, NULL, 'monitor:job:delete', NULL, 3, 0, 1),
    (56, 52, '修改状态', 'button', NULL, NULL, 'monitor:job:changeStatus', NULL, 4, 0, 1),
    (57, 52, '立即执行', 'button', NULL, NULL, 'monitor:job:run', NULL, 5, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 52 AND 57;

INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, remark)
VALUES
    ('演示任务', 'DEFAULT', 'demoTask.runDemo', '0/30 * * * * ?', '1', 1, 0, '每 30 秒执行一次的演示任务');

UPDATE sys_role SET data_scope = 1 WHERE code = 'admin';

