-- 消息多渠道模块：渠道配置表 + 发送记录表 + 菜单（id 95~102，当前最大 menu id = 94）
CREATE TABLE sys_channel_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    channel_type VARCHAR(20) NOT NULL COMMENT '渠道类型 MAIL/SMS/DINGTALK/WECOM',
    channel_name VARCHAR(50) NOT NULL COMMENT '渠道名称',
    config_json TEXT NOT NULL COMMENT '渠道参数 JSON（SMTP/网关/webhook 等）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    sort INT NOT NULL DEFAULT 0,
    description VARCHAR(255) COMMENT '渠道描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_channel_config_type (tenant_id, channel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息渠道配置';

CREATE TABLE sys_channel_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    channel_type VARCHAR(20) NOT NULL COMMENT '渠道类型',
    channel_id BIGINT NOT NULL COMMENT '渠道配置 ID',
    target VARCHAR(500) COMMENT '接收目标',
    title VARCHAR(200) COMMENT '标题',
    content VARCHAR(4000) COMMENT '内容',
    status TINYINT NOT NULL COMMENT '状态 1成功 0失败',
    error_msg VARCHAR(1000) COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_channel_log_type (tenant_id, channel_type),
    KEY idx_channel_log_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道发送记录';

-- 顶级目录 + 子菜单 + 按钮权限
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (95, 0, '消息通知', 'dir', '/notice', NULL, NULL, 'NotificationOutlined', 8, 1, 1),
    (96, 95, '渠道配置', 'menu', 'channel', 'notice/channel', 'notice:channel:list', 'ApiOutlined', 1, 1, 1),
    (97, 95, '发送记录', 'menu', 'channel-log', 'notice/channel-log', 'notice:channel:log', 'HistoryOutlined', 2, 1, 1),
    (98, 96, '新增渠道', 'button', NULL, NULL, 'notice:channel:add', NULL, 1, 0, 1),
    (99, 96, '编辑渠道', 'button', NULL, NULL, 'notice:channel:edit', NULL, 2, 0, 1),
    (100, 96, '修改状态', 'button', NULL, NULL, 'notice:channel:status', NULL, 3, 0, 1),
    (101, 96, '删除渠道', 'button', NULL, NULL, 'notice:channel:delete', NULL, 4, 0, 1),
    (102, 96, '发送消息', 'button', NULL, NULL, 'notice:channel:send', NULL, 5, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 95 AND 102;
