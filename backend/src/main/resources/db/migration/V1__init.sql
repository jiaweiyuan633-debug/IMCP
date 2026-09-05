CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50),
    avatar VARCHAR(255),
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT NOT NULL DEFAULT 1,
    last_login_time DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user';

CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role';

CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    path VARCHAR(200),
    component VARCHAR(200),
    perm VARCHAR(100),
    icon VARCHAR(100),
    sort INT NOT NULL DEFAULT 0,
    visible TINYINT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    KEY idx_sys_menu_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='menu';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user role';

CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role menu';

CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    ip VARCHAR(64),
    user_agent VARCHAR(255),
    status TINYINT NOT NULL,
    message VARCHAR(255),
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_login_log_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='login log';

CREATE TABLE sys_oper_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    module VARCHAR(50),
    action VARCHAR(50),
    method VARCHAR(200),
    request_url VARCHAR(255),
    request_method VARCHAR(10),
    params TEXT,
    result TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    error_msg VARCHAR(1000),
    ip VARCHAR(64),
    duration_ms BIGINT,
    oper_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='oper log';

CREATE TABLE ai_service_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    timeout_seconds INT NOT NULL DEFAULT 60,
    enabled TINYINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_ai_service_config_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ai service config';

CREATE TABLE ai_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no VARCHAR(64) NOT NULL,
    biz_type VARCHAR(50) NOT NULL,
    biz_id BIGINT NULL,
    service_code VARCHAR(50) NOT NULL DEFAULT 'default',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    params_json JSON NULL,
    error_msg VARCHAR(1000),
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    timeout_seconds INT NOT NULL DEFAULT 60,
    callback_url VARCHAR(255),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_task_task_no (task_no),
    KEY idx_ai_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ai task';

CREATE TABLE ai_task_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    result_type VARCHAR(50),
    result_json JSON NULL,
    raw_data LONGTEXT,
    duration_ms BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_task_result_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ai task result';

INSERT INTO sys_user (id, username, password, nickname, email, status)
VALUES (1, 'admin', '$2a$10$46ip4ZqIPk1MncJM9QDzCOxPKNc/N6RzWcMxgI9XQfgspPWLMFGzK', '系统管理员', 'admin@example.com', 1);

INSERT INTO sys_role (id, code, name, description, sort)
VALUES (1, 'admin', '超级管理员', '拥有全部权限', 1),
       (2, 'user', '普通用户', '默认业务账号', 2);

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (1, 0, '首页', 'dir', '/dashboard', NULL, NULL, 'DashboardOutlined', 1, 1, 1),
    (2, 1, '数据看板', 'menu', 'dashboard', 'dashboard/index', NULL, 'BarChartOutlined', 1, 1, 1),
    (3, 0, '系统管理', 'dir', '/system', NULL, NULL, 'SettingOutlined', 2, 1, 1),
    (4, 3, '用户管理', 'menu', 'user', 'system/user', 'system:user:list', 'UserOutlined', 1, 1, 1),
    (5, 3, '角色管理', 'menu', 'role', 'system/role', 'system:role:list', 'TeamOutlined', 2, 1, 1),
    (6, 3, '菜单管理', 'menu', 'menu', 'system/menu', 'system:menu:list', 'MenuOutlined', 3, 1, 1),
    (7, 0, '系统监控', 'dir', '/monitor', NULL, NULL, 'MonitorOutlined', 3, 1, 1),
    (8, 7, '登录日志', 'menu', 'login-log', 'monitor/login-log', 'monitor:login-log:list', 'HistoryOutlined', 1, 1, 1),
    (9, 7, '操作日志', 'menu', 'oper-log', 'monitor/oper-log', 'monitor:oper-log:list', 'FileTextOutlined', 2, 1, 1),
    (10, 7, '在线用户', 'menu', 'online', 'monitor/online', 'monitor:online:list', 'WifiOutlined', 3, 1, 1),
    (11, 7, '缓存管理', 'menu', 'cache', 'monitor/cache', 'monitor:cache:list', 'DatabaseOutlined', 4, 1, 1),
    (12, 0, 'AI 管理', 'dir', '/ai', NULL, NULL, 'RobotOutlined', 4, 1, 1),
    (13, 12, 'AI 任务', 'menu', 'task', 'ai/task', 'ai:task:list', 'CarryOutOutlined', 1, 1, 1),
    (14, 12, 'AI 配置', 'menu', 'config', 'ai/config', 'ai:config:list', 'ApiOutlined', 2, 1, 1),
    (15, 0, '个人中心', 'menu', '/profile', 'profile/index', NULL, 'UserOutlined', 5, 1, 1);

INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (2, 2), (2, 15);

INSERT INTO ai_service_config (id, code, name, base_url, api_key, timeout_seconds, enabled)
VALUES (1, 'default', 'FastAPI AI 服务', 'http://localhost:8000', 'dev-ai-service-token', 60, 1);
