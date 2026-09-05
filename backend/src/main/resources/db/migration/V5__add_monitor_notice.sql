CREATE TABLE sys_sql_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sql_text VARCHAR(2000) NOT NULL,
    method VARCHAR(100),
    duration_ms BIGINT NOT NULL,
    success TINYINT NOT NULL DEFAULT 1,
    error_msg VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_sql_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sql monitor log';

CREATE TABLE sys_notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notice_title VARCHAR(100) NOT NULL,
    notice_type TINYINT NOT NULL DEFAULT 1,
    notice_content TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sys_notice_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='notice';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (58, 7, '服务器监控', 'menu', 'server', 'monitor/server', 'monitor:server:list', 'FundOutlined', 6, 1, 1),
    (59, 7, 'SQL 监控', 'menu', 'sql', 'monitor/sql', 'monitor:sql:list', 'CodeOutlined', 7, 1, 1),
    (60, 3, '通知公告', 'menu', 'notice', 'system/notice', 'system:notice:list', 'NotificationOutlined', 8, 1, 1),
    (61, 60, '新增公告', 'button', NULL, NULL, 'system:notice:add', NULL, 1, 0, 1),
    (62, 60, '编辑公告', 'button', NULL, NULL, 'system:notice:edit', NULL, 2, 0, 1),
    (63, 60, '删除公告', 'button', NULL, NULL, 'system:notice:delete', NULL, 3, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 58 AND 63;

INSERT INTO sys_notice (notice_title, notice_type, notice_content, status, created_by)
VALUES ('欢迎使用双端管理脚手架', 1, '这是一条演示通知公告，可在通知公告模块中维护。', 1, 1);

