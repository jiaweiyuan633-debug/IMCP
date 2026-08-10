-- 字段级审计日志：记录重要数据修改前后快照与字段级 diff，用于合规追溯（等保"对重要数据进行修改时进行审计"）
-- 由 @FieldAudit 注解切面写入，展示在「监控 → 审计日志 → 字段级审计」
CREATE TABLE sys_field_audit_log (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    user_id BIGINT NULL,
    module VARCHAR(50) COMMENT '业务模块名',
    entity_name VARCHAR(100) COMMENT '被审计实体类名',
    entity_id BIGINT NULL COMMENT '被审计记录主键',
    action VARCHAR(20) NOT NULL DEFAULT 'UPDATE' COMMENT 'CREATE/UPDATE/DELETE',
    changed_fields TEXT COMMENT '字段级变更 JSON: [{field,label,before,after}]',
    before_data MEDIUMTEXT COMMENT '变更前完整快照',
    after_data MEDIUMTEXT COMMENT '变更后完整快照',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_field_audit_entity (entity_name, entity_id),
    KEY idx_sys_field_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='field-level audit log';
