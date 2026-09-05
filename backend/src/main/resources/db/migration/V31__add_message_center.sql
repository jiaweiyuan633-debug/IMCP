CREATE TABLE sys_message (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    sender_id BIGINT NULL,
    receiver_id BIGINT NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    title VARCHAR(200) NOT NULL,
    content TEXT,
    biz_type VARCHAR(50),
    biz_id BIGINT,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_sys_message_tenant_receiver (tenant_id, receiver_id, created_at),
    KEY idx_sys_message_type (message_type),
    KEY idx_sys_message_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='message center';

CREATE TABLE sys_message_read (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_message_read (message_id, user_id),
    KEY idx_sys_message_read_user (user_id, read_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='message read';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (82, 3, '消息中心', 'menu', 'message', 'system/message', 'system:message:list', 'MessageOutlined', 9, 1, 1),
    (83, 82, '发送消息', 'button', NULL, NULL, 'system:message:add', NULL, 1, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (82, 83);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id = 82;

INSERT INTO sys_message (tenant_id, sender_id, receiver_id, message_type, title, content, priority, created_by)
VALUES (1, 1, NULL, 'SYSTEM', '欢迎使用消息中心', '系统消息、审批待办和实时通知将在这里汇总。', 'NORMAL', 1);
