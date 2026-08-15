-- 注册审计日志表的数据权限映射（批次8a）
-- sys_audit_log / sys_field_audit_log 的查询与导出此前不受行级数据权限控制：
-- MonitorAuditService.page/export、MonitorFieldAuditService.page 已标注
-- @DataScope(tables = {...})，此处将「表名 -> 用户ID关联列」登记到 sys_data_permission，
-- 非管理员登录后查询/导出审计日志将按"当前用户可见的用户ID集合"过滤（列 user_id）。
-- 管理员角色（admin）经 DataScopeAspect.isAdmin 短路，不施加过滤，行为不变。
INSERT INTO sys_data_permission (table_name, user_column, username_column, remark) VALUES
    ('sys_audit_log',        'user_id', NULL, '操作审计日志：按操作人用户ID过滤'),
    ('sys_field_audit_log',  'user_id', NULL, '字段审计日志：按操作人用户ID过滤');
