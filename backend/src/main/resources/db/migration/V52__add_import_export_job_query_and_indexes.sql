-- 批次4 修复 W3：导入导出任务表 query_json 透传列 + 状态机检索索引。
-- query_json：导出筛选参数（创建时序列化落库，处理器反序列化后透传 handler.export），
-- 保证导出按用户筛选范围而非全量拉取；同时支撑 export-max-rows 行数上限的早期拒绝。
ALTER TABLE import_export_job
    ADD COLUMN query_json TEXT NULL COMMENT '导出筛选参数 JSON' AFTER file_name,
    -- 轮询扫描（PENDING）+ 超时回收（PROCESSING + updated_at）共用 (status, updated_at)
    ADD KEY idx_ie_job_status (status, updated_at),
    -- 租户分页按 status / type 过滤
    ADD KEY idx_ie_job_tenant_status (tenant_id, status),
    ADD KEY idx_ie_job_tenant_type (tenant_id, type);
