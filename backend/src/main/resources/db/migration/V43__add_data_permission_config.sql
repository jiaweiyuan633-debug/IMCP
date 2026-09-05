-- 数据权限表-列映射配置（批次2b 数据权限行级表达式可配置化）
-- 此前 DataScopeInnerInterceptor 以硬编码 if/else 维护「表名 -> 关联用户列」，新增受控表需改 Java 代码。
-- 本表将该映射下沉为运行时配置：管理员配置受控表与关联列后即生效，无需发版。
-- user_column 按「当前用户可见的用户ID集合」过滤（列值为用户ID）；
-- username_column 按「当前用户可见的用户名集合」过滤（列值为用户名），设置时优先于 user_column。
CREATE TABLE sys_data_permission (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(128) NOT NULL COMMENT '受控表名（全小写）',
    user_column VARCHAR(128) NULL COMMENT '用户ID关联列名，如 id/created_by/user_id',
    username_column VARCHAR(128) NULL COMMENT '用户名关联列名，如 username；设置时优先于 user_column',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 0 停用',
    remark VARCHAR(255) NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dp_table (table_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='数据权限表-列映射配置';

-- 种子数据：与原硬编码行为完全对齐，保证升级后行级权限语义不变。
INSERT INTO sys_data_permission (table_name, user_column, username_column, remark) VALUES
    ('sys_user',      'id',        NULL,       '系统用户：按用户ID集合过滤'),
    ('ai_task',       'created_by', NULL,      'AI任务：按创建人用户ID过滤'),
    ('sys_oper_log',  'user_id',   NULL,       '操作日志：按操作人用户ID过滤'),
    ('sys_login_log', NULL,        'username', '登录日志：按用户名集合过滤');
