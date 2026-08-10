-- 认证扩展：第三方 OAuth2 登录（微信扫码/GitHub/Gitee）+ SSO 授权码服务
CREATE TABLE sys_oauth_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    provider VARCHAR(20) NOT NULL COMMENT '提供方 wechat/github/gitee',
    app_id VARCHAR(100) NOT NULL COMMENT '客户端 ID',
    app_secret VARCHAR(255) NOT NULL COMMENT '客户端密钥',
    redirect_uri VARCHAR(255) COMMENT '回调地址（留空用前端回跳地址）',
    scope VARCHAR(255) COMMENT '授权范围',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    sort INT NOT NULL DEFAULT 0,
    remark VARCHAR(255) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_oauth_config_tenant_provider (tenant_id, provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方登录配置';

-- 用户-第三方账号绑定
CREATE TABLE sys_user_oauth (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL COMMENT '平台用户 ID',
    provider VARCHAR(20) NOT NULL COMMENT '提供方',
    open_id VARCHAR(128) NOT NULL COMMENT '第三方唯一 ID',
    union_id VARCHAR(128) COMMENT '第三方开放平台 union_id',
    nickname VARCHAR(100) COMMENT '第三方昵称',
    avatar VARCHAR(255) COMMENT '第三方头像',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_oauth (tenant_id, provider, open_id),
    KEY idx_user_oauth_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户第三方绑定';

-- SSO 第三方应用（本平台作为 OAuth2 授权服务）
CREATE TABLE sys_oauth_client (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    client_name VARCHAR(100) NOT NULL COMMENT '应用名称',
    client_id VARCHAR(64) NOT NULL COMMENT 'client_id',
    client_secret VARCHAR(128) NOT NULL COMMENT 'client_secret',
    redirect_uri VARCHAR(255) COMMENT '授权回调地址',
    scope VARCHAR(255) COMMENT '授权范围',
    enabled TINYINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_oauth_client_tenant_id (tenant_id, client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSO 第三方应用';

-- 菜单：挂系统管理 dir(3)
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (114, 3, '第三方登录', 'menu', 'oauth-config', 'system/oauth-config', 'system:oauth:list', 'WechatOutlined', 8, 1, 1),
    (115, 3, 'SSO 应用', 'menu', 'sso-client', 'system/sso-client', 'system:oauth:client:list', 'TeamOutlined', 9, 1, 1),
    (116, 114, '新增配置', 'button', NULL, NULL, 'system:oauth:add', NULL, 1, 0, 1),
    (117, 114, '编辑配置', 'button', NULL, NULL, 'system:oauth:edit', NULL, 2, 0, 1),
    (118, 114, '修改状态', 'button', NULL, NULL, 'system:oauth:status', NULL, 3, 0, 1),
    (119, 114, '删除配置', 'button', NULL, NULL, 'system:oauth:delete', NULL, 4, 0, 1),
    (120, 115, '新增应用', 'button', NULL, NULL, 'system:oauth:client:add', NULL, 1, 0, 1),
    (121, 115, '编辑应用', 'button', NULL, NULL, 'system:oauth:client:edit', NULL, 2, 0, 1),
    (122, 115, '修改状态', 'button', NULL, NULL, 'system:oauth:client:status', NULL, 3, 0, 1),
    (123, 115, '删除应用', 'button', NULL, NULL, 'system:oauth:client:delete', NULL, 4, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 114 AND 123;
