CREATE TABLE sys_alert_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_name VARCHAR(100) NOT NULL,
    metric VARCHAR(50) NOT NULL,
    operator VARCHAR(10) NOT NULL DEFAULT 'gt',
    threshold DECIMAL(10, 2) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='server alert rule';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (71, 7, '告警规则', 'menu', 'alert', 'monitor/alert', 'monitor:alert:list', 'AlertOutlined', 8, 1, 1),
    (72, 71, '新增规则', 'button', NULL, NULL, 'monitor:alert:add', NULL, 1, 0, 1),
    (73, 71, '编辑规则', 'button', NULL, NULL, 'monitor:alert:edit', NULL, 2, 0, 1),
    (74, 71, '删除规则', 'button', NULL, NULL, 'monitor:alert:delete', NULL, 3, 0, 1),
    (75, 71, '立即检查', 'button', NULL, NULL, 'monitor:alert:run', NULL, 4, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 71 AND 75;

INSERT INTO sys_alert_rule
    (rule_name, metric, operator, threshold, enabled, remark)
VALUES
    ('CPU 使用率告警', 'CPU_USAGE', 'gt', 80.00, 1, 'CPU 使用率超过 80% 时发送通知'),
    ('内存使用率告警', 'MEMORY_USAGE', 'gt', 85.00, 1, '内存使用率超过 85% 时发送通知');
