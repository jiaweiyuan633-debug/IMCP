ALTER TABLE sys_user ADD COLUMN dept_id BIGINT NULL AFTER id;

CREATE TABLE sys_dept (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NOT NULL DEFAULT 0,
    ancestors VARCHAR(255) NOT NULL DEFAULT '',
    dept_name VARCHAR(50) NOT NULL,
    order_num INT NOT NULL DEFAULT 0,
    leader VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_sys_dept_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='department';

CREATE TABLE sys_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_code VARCHAR(50) NOT NULL,
    post_name VARCHAR(50) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_sys_post_code (post_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='post';

CREATE TABLE sys_user_post (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user post';

CREATE TABLE sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_name VARCHAR(100) NOT NULL,
    dict_type VARCHAR(100) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dict type';

CREATE TABLE sys_dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type VARCHAR(100) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_sort INT NOT NULL DEFAULT 0,
    list_class VARCHAR(100),
    is_default TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_dict_data (dict_type, dict_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dict data';

CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_name VARCHAR(100) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) NOT NULL,
    config_type TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='config';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (33, 3, '部门管理', 'menu', 'dept', 'system/dept', 'system:dept:list', 'ApartmentOutlined', 4, 1, 1),
    (34, 3, '岗位管理', 'menu', 'post', 'system/post', 'system:post:list', 'IdcardOutlined', 5, 1, 1),
    (35, 3, '字典管理', 'menu', 'dict', 'system/dict', 'system:dict:list', 'BookOutlined', 6, 1, 1),
    (36, 3, '参数配置', 'menu', 'config', 'system/config', 'system:config:list', 'ControlOutlined', 7, 1, 1),
    (37, 33, '新增部门', 'button', NULL, NULL, 'system:dept:add', NULL, 1, 0, 1),
    (38, 33, '编辑部门', 'button', NULL, NULL, 'system:dept:edit', NULL, 2, 0, 1),
    (39, 33, '删除部门', 'button', NULL, NULL, 'system:dept:delete', NULL, 3, 0, 1),
    (40, 34, '新增岗位', 'button', NULL, NULL, 'system:post:add', NULL, 1, 0, 1),
    (41, 34, '编辑岗位', 'button', NULL, NULL, 'system:post:edit', NULL, 2, 0, 1),
    (42, 34, '删除岗位', 'button', NULL, NULL, 'system:post:delete', NULL, 3, 0, 1),
    (43, 35, '新增字典类型', 'button', NULL, NULL, 'system:dict:add', NULL, 1, 0, 1),
    (44, 35, '编辑字典类型', 'button', NULL, NULL, 'system:dict:edit', NULL, 2, 0, 1),
    (45, 35, '删除字典类型', 'button', NULL, NULL, 'system:dict:delete', NULL, 3, 0, 1),
    (46, 35, '新增字典数据', 'button', NULL, NULL, 'system:dict:data:add', NULL, 4, 0, 1),
    (47, 35, '编辑字典数据', 'button', NULL, NULL, 'system:dict:data:edit', NULL, 5, 0, 1),
    (48, 35, '删除字典数据', 'button', NULL, NULL, 'system:dict:data:delete', NULL, 6, 0, 1),
    (49, 36, '新增参数', 'button', NULL, NULL, 'system:config:add', NULL, 1, 0, 1),
    (50, 36, '编辑参数', 'button', NULL, NULL, 'system:config:edit', NULL, 2, 0, 1),
    (51, 36, '删除参数', 'button', NULL, NULL, 'system:config:delete', NULL, 3, 0, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 33 AND 51;

INSERT INTO sys_dept (id, parent_id, ancestors, dept_name, order_num, leader, status)
VALUES (1, 0, '0', '总公司', 1, '系统管理员', 1),
       (2, 1, '0,1', '研发部', 1, '研发负责人', 1),
       (3, 1, '0,1', '测试部', 2, '测试负责人', 1);

INSERT INTO sys_post (id, post_code, post_name, sort, status, description)
VALUES (1, 'ceo', '总经理', 1, 1, '公司负责人'),
       (2, 'dev', '开发工程师', 2, 1, '研发岗位'),
       (3, 'qa', '测试工程师', 3, 1, '测试岗位');

INSERT INTO sys_user_post (user_id, post_id)
VALUES (1, 1);

UPDATE sys_user SET dept_id = 1 WHERE id = 1;

INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark)
VALUES (1, '通用状态', 'sys_normal_disable', 1, '启用/停用'),
       (2, '用户状态', 'sys_user_status', 1, '用户状态'),
       (3, '是否', 'sys_yes_no', 1, '是/否'),
       (4, 'AI 任务状态', 'ai_task_status', 1, 'AI 任务状态');

INSERT INTO sys_dict_data
    (dict_type, dict_label, dict_value, dict_sort, list_class, is_default, status)
VALUES
    ('sys_normal_disable', '启用', '1', 1, 'success', 1, 1),
    ('sys_normal_disable', '停用', '0', 2, 'danger', 0, 1),
    ('sys_user_status', '正常', '1', 1, 'success', 1, 1),
    ('sys_user_status', '停用', '0', 2, 'danger', 0, 1),
    ('sys_yes_no', '是', 'Y', 1, 'success', 1, 1),
    ('sys_yes_no', '否', 'N', 2, 'danger', 0, 1),
    ('ai_task_status', '待处理', 'PENDING', 1, 'default', 0, 1),
    ('ai_task_status', '排队中', 'QUEUED', 2, 'processing', 0, 1),
    ('ai_task_status', '执行中', 'RUNNING', 3, 'processing', 0, 1),
    ('ai_task_status', '成功', 'SUCCEEDED', 4, 'success', 0, 1),
    ('ai_task_status', '失败', 'FAILED', 5, 'danger', 0, 1),
    ('ai_task_status', '已取消', 'CANCELLED', 6, 'warning', 0, 1);

INSERT INTO sys_config (id, config_name, config_key, config_value, config_type, remark)
VALUES (1, '初始密码', 'sys.user.initPassword', 'admin123', 0, '新建用户默认密码'),
       (2, '登录验证码开关', 'sys.account.captchaEnabled', 'false', 1, '是否开启登录验证码');

