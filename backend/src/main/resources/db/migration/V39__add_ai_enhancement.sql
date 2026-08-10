-- AI 增强：模型网关字段 + Prompt 模板 + RAG 知识库（菜单 id 103~113，挂 AI 管理 dir=12）
ALTER TABLE ai_service_config
    ADD COLUMN model VARCHAR(100) NULL COMMENT '模型名称（OpenAI 兼容直连时必填）' AFTER name,
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'local' COMMENT '提供方 openai/local' AFTER code;

-- Prompt 模板
CREATE TABLE ai_prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(50) NOT NULL COMMENT '模板编码',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    content TEXT NOT NULL COMMENT '模板内容，{var} 占位符',
    variables VARCHAR(500) COMMENT '变量列表 JSON',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    sort INT NOT NULL DEFAULT 0,
    description VARCHAR(255) COMMENT '模板描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_prompt_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Prompt 模板';

-- RAG 知识库
CREATE TABLE ai_knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description VARCHAR(255) COMMENT '知识库描述',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识库';

-- 知识库文档（全文检索回退；Milvus 启用时向量化后同样落库）
CREATE TABLE ai_knowledge_doc (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    base_id BIGINT NOT NULL COMMENT '知识库 ID',
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    content MEDIUMTEXT COMMENT '文档内容',
    chunk_index INT NOT NULL DEFAULT 0 COMMENT '分块序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_knowledge_doc_base (tenant_id, base_id),
    FULLTEXT KEY ft_knowledge_doc_content (title, content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 知识库文档';

-- 菜单：挂 AI 管理 dir(12)
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    (103, 12, 'Prompt 模板', 'menu', 'prompt', 'ai/prompt', 'ai:prompt:list', 'FileTextOutlined', 1, 1, 1),
    (104, 12, '知识库', 'menu', 'knowledge', 'ai/knowledge', 'ai:knowledge:list', 'DatabaseOutlined', 2, 1, 1),
    (113, 12, 'AI 对话', 'menu', 'chat', 'ai/chat', 'ai:chat', 'MessageOutlined', 3, 1, 1),
    (105, 103, '新增模板', 'button', NULL, NULL, 'ai:prompt:add', NULL, 1, 0, 1),
    (106, 103, '编辑模板', 'button', NULL, NULL, 'ai:prompt:edit', NULL, 2, 0, 1),
    (107, 103, '删除模板', 'button', NULL, NULL, 'ai:prompt:delete', NULL, 3, 0, 1),
    (108, 104, '新增知识库', 'button', NULL, NULL, 'ai:knowledge:add', NULL, 1, 0, 1),
    (109, 104, '编辑知识库', 'button', NULL, NULL, 'ai:knowledge:edit', NULL, 2, 0, 1),
    (110, 104, '删除知识库', 'button', NULL, NULL, 'ai:knowledge:delete', NULL, 3, 0, 1),
    (111, 104, '新增文档', 'button', NULL, NULL, 'ai:knowledge:doc:add', NULL, 4, 0, 1),
    (112, 104, '删除文档', 'button', NULL, NULL, 'ai:knowledge:doc:delete', NULL, 5, 0, 1);

-- 授权给超管 role_id=1
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 103 AND 113;
