-- 消息模板：可复用模板 + 参数渲染 + 富文本（HTML）内容类型。菜单 id 129~134（当前最大 menu id = 128）
CREATE TABLE sys_message_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    template_code VARCHAR(50) NOT NULL COMMENT '模板编码，租户内唯一',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    message_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '消息类型 SYSTEM/TODO/NOTICE',
    title_template VARCHAR(200) NOT NULL COMMENT '标题模板，支持 ${key} 占位符',
    content_template TEXT NOT NULL COMMENT '内容模板，支持 ${key} 占位符',
    content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '内容类型 TEXT/HTML',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_message_template_code (tenant_id, template_code),
    KEY idx_message_template_type (message_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板';

-- 消息内容增加类型标记：TEXT 纯文本 / HTML 富文本（前端按类型渲染，不转义 HTML）
ALTER TABLE sys_message ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT '内容类型 TEXT/HTML';

INSERT INTO sys_menu
    (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (129, 3, '消息模板', 'menu', 'message-template', 'system/message-template', 'system:message:template:list', 'FileTextOutlined', 10, 1, 1),
    (130, 129, '新增模板', 'button', NULL, NULL, 'system:message:template:add', NULL, 1, 0, 1),
    (131, 129, '编辑模板', 'button', NULL, NULL, 'system:message:template:edit', NULL, 2, 0, 1),
    (132, 129, '删除模板', 'button', NULL, NULL, 'system:message:template:delete', NULL, 3, 0, 1),
    (133, 129, '按模板发送', 'button', NULL, NULL, 'system:message:template:send', NULL, 4, 0, 1),
    (134, 129, '修改状态', 'button', NULL, NULL, 'system:message:template:status', NULL, 5, 0, 1);

-- 授权给超管 role_id=1 与运维 role_id=2
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 129 AND 134;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id = 129;
