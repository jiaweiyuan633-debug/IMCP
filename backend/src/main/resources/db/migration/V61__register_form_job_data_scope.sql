-- 注册业务表的数据权限映射（批次10 数据权限业务表扩展，R4-1.37）
-- 表单提交记录（form_instance.submitter_id）与导入导出任务（import_export_job.created_by）
-- 的分页查询已标注 @DataScope(tables = {...})，此处将「表名 -> 用户ID关联列」登记到
-- sys_data_permission：非管理员登录后分页仅返回"当前用户可见的用户ID集合"内的记录，
-- 管理员角色（admin）经 DataScopeAspect.isAdmin 短路不施加过滤，行为不变。
--
-- 数据权限语义矩阵（明确不施加行级过滤的表及原因，见 docs/database/README.md）：
--   report_definition / form_definition / screen_template：租户内全局共享的配置类数据，
--     由租户隔离 + builtin/status 业务条件控制可见性，按创建人过滤会破坏共享语义；
--   sys_notice / sys_message：公告全员可见、消息按接收人定向分发，由业务查询条件控制；
--   sys_channel_config / ai_service_config 等配置表：仅管理员可管理，不设行级权限。
INSERT INTO sys_data_permission (table_name, user_column, username_column, remark) VALUES
    ('form_instance',     'submitter_id', NULL, '表单提交记录：按提交人用户ID过滤'),
    ('import_export_job', 'created_by',   NULL, '导入导出任务：按创建人用户ID过滤');
