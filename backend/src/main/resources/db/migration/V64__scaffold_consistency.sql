-- =====================================================================
-- V64：脚手架一致性收口（唯一键释放支撑 + 关键查询索引 + id 无符号对齐）
-- 目标：
--  a) 为「逻辑删除 + 业务编码唯一键」的批次后新增表补齐删除语义的落库前提——
--     Service 层已按 UniqueKeyRelease 方案在逻辑删除前把业务编码改为
--     「原编码#del#时间戳」以释放唯一键（report_definition / form_definition /
--     import_export_template / device_thing_model / screen_template / sys_device /
--     sys_message_template / sys_oauth_config / sys_oauth_client）。
--     编码列在本迁移统一加宽，保证最大长度编码 + 释放后缀(#del#+17位)不触发
--     "Data too long"，删除路径不因后缀溢出而失败。
--  b) 补齐多租户查询/审计检索的关键索引（索引名与既有 DDL 无冲突，见各表注释）。
--  c) 把 V28 之后新增的业务表 id 与引用列统一为 BIGINT UNSIGNED（V28 已对存量基础表
--     完成同样对齐），新模块表与基础表主键类型口径一致。
-- =====================================================================

-- ---------------------------------------------------------------------
-- a) 业务编码列加宽（唯一键释放后缀空间）——仅加宽不缩窄，向后兼容
-- ---------------------------------------------------------------------
ALTER TABLE report_definition      MODIFY COLUMN code         VARCHAR(96) NOT NULL COMMENT '报表编码';
ALTER TABLE form_definition        MODIFY COLUMN code         VARCHAR(96) NOT NULL COMMENT '表单编码';
ALTER TABLE import_export_template MODIFY COLUMN code         VARCHAR(96) NOT NULL COMMENT '模板编码';
ALTER TABLE device_thing_model     MODIFY COLUMN device_type  VARCHAR(96) NOT NULL COMMENT '物模型类型编码';
ALTER TABLE screen_template        MODIFY COLUMN code         VARCHAR(96) NOT NULL COMMENT '模板编码（内置模板全局唯一）';
ALTER TABLE sys_device             MODIFY COLUMN device_code  VARCHAR(82) NOT NULL COMMENT '设备编码';
ALTER TABLE sys_message_template   MODIFY COLUMN template_code VARCHAR(82) NOT NULL COMMENT '模板编码，租户内唯一';
ALTER TABLE sys_oauth_config       MODIFY COLUMN provider     VARCHAR(52) NOT NULL COMMENT '提供方 wechat/github/gitee';
ALTER TABLE sys_oauth_client       MODIFY COLUMN client_id    VARCHAR(96) NOT NULL COMMENT 'client_id';

-- ---------------------------------------------------------------------
-- b) 关键查询索引
-- ---------------------------------------------------------------------

-- 审计日志按租户+操作人+时间倒序检索（监控审计页高频路径，原仅 created_at 单列索引）
ALTER TABLE sys_audit_log
    ADD KEY idx_sys_audit_log_tenant_user_time (tenant_id, user_id, created_at);

-- 字段级审计按租户+实体+主键定位变更历史（与既有 idx_sys_field_audit_entity 互补：
-- 原索引无租户前缀，跨租户检索需过滤全库）
ALTER TABLE sys_field_audit_log
    ADD KEY idx_sys_field_audit_tenant_entity_time (tenant_id, entity_name, entity_id, created_at);

-- 工作流日志按租户+流程实例检索（V12 补过 tenant_id 列但无复合索引）
ALTER TABLE sys_workflow_log
    ADD KEY idx_sys_workflow_log_tenant_workflow (tenant_id, workflow_id);

-- 表单提交记录按租户+提交人+提交时间查询（提交历史列表/个人提交统计）
ALTER TABLE form_instance
    ADD KEY idx_form_instance_tenant_submitter_time (tenant_id, submitter_id, submitted_at);

-- warm-flow 系列表租户前缀索引：tenant_id 拦截器按租户过滤时避免全表扫描。
-- flow_definition 已有 (tenant_id, flow_code) 索引，不重复添加。
ALTER TABLE flow_node
    ADD KEY idx_flow_node_tenant_definition (tenant_id, definition_id);
ALTER TABLE flow_skip
    ADD KEY idx_flow_skip_tenant_definition (tenant_id, definition_id);
ALTER TABLE flow_instance
    ADD KEY idx_flow_instance_tenant_business (tenant_id, business_id);
ALTER TABLE flow_task
    ADD KEY idx_flow_task_tenant_instance (tenant_id, instance_id);
ALTER TABLE flow_his_task
    ADD KEY idx_flow_his_task_tenant_instance (tenant_id, instance_id);
ALTER TABLE flow_user
    ADD KEY idx_flow_user_tenant_associated (tenant_id, associated);

-- ai_task_result 按需评估后不加索引：查询入口恒为 task_id（已有 idx_ai_task_result_task_id），
-- 无按租户直接列结果集的界面；避免对 JSON/LONGTEXT 大行表过度建索引。

-- ---------------------------------------------------------------------
-- c) V28 之后新增业务表 id/引用列 → BIGINT UNSIGNED（与 V28 基础表口径一致；
--    所有主键均为自增正值，无符号化不改变应用读写语义）
-- ---------------------------------------------------------------------
ALTER TABLE report_definition      MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
ALTER TABLE form_definition        MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
-- form_instance.id 与其引用 form_definition.id 的 form_id 同步无符号化
ALTER TABLE form_instance          MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                   MODIFY COLUMN form_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE screen_template        MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
ALTER TABLE import_export_template MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
-- import_export_job：id、模板引用 template_id、文件引用(sys_file.id 已无符号化)对齐
ALTER TABLE import_export_job      MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                   MODIFY COLUMN template_id BIGINT UNSIGNED NOT NULL,
                                   MODIFY COLUMN file_id BIGINT UNSIGNED NULL,
                                   MODIFY COLUMN result_file_id BIGINT UNSIGNED NULL;
ALTER TABLE device_thing_model     MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
-- device_telemetry.device_id 引用 sys_device.id，同步无符号化（遥测纯追加无 deleted）
ALTER TABLE device_telemetry       MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                   MODIFY COLUMN device_id BIGINT UNSIGNED NOT NULL;
ALTER TABLE sys_device             MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_message_template   MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_oauth_config       MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
ALTER TABLE sys_oauth_client       MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
-- sys_user_oauth.user_id 引用 sys_user.id（V28 已无符号化），同步对齐
ALTER TABLE sys_user_oauth         MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                   MODIFY COLUMN user_id BIGINT UNSIGNED NOT NULL;

-- 说明：其余 V28 之后表（sys_api_perm/sys_field_audit_log(本就 UNSIGNED)/outbox/mcp/共享字典
-- 等）无本迁移涉及的模块化唯一键/引用诉求，未纳入无符号化范围；后续新建表应直接按
-- BIGINT UNSIGNED 定义（与 V28 约定一致）。
