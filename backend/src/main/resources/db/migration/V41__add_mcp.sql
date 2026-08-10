-- MCP 扩展：外部 MCP Server 配置（本平台作为 MCP Client 消费外部 AI 工具）
CREATE TABLE sys_mcp_server (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(100) NOT NULL COMMENT '服务名称',
    url VARCHAR(255) NOT NULL COMMENT 'MCP Server SSE 地址',
    auth_token VARCHAR(255) COMMENT 'Bearer 认证令牌（留空为匿名）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    sort INT NOT NULL DEFAULT 0,
    remark VARCHAR(255) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部 MCP Server 配置';

-- 菜单：挂系统管理 dir(3)
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (124, 3, 'MCP 服务', 'menu', 'mcp-server', 'system/mcp-server', 'system:mcp:list', 'ApiOutlined', 10, 1, 1),
    (125, 124, '新增服务', 'button', NULL, NULL, 'system:mcp:add', NULL, 1, 0, 1),
    (126, 124, '编辑服务', 'button', NULL, NULL, 'system:mcp:edit', NULL, 2, 0, 1),
    (127, 124, '修改状态', 'button', NULL, NULL, 'system:mcp:status', NULL, 3, 0, 1),
    (128, 124, '删除服务', 'button', NULL, NULL, 'system:mcp:delete', NULL, 4, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 124 AND 128;
