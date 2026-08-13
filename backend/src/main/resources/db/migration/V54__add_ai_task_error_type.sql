-- R4-1.20：AI 失败原因分类（timeout / non_retryable / retries_exhausted）随回调落库。
-- 此前分类仅在 AI 侧指标标签与死信记录中，回调契约不携带，后端系统记录无法区分
-- 瞬时超时（值得重试）与确定性错误（重试无意义）。
ALTER TABLE ai_task
    ADD COLUMN error_type VARCHAR(32) NULL COMMENT '失败原因分类：timeout/non_retryable/retries_exhausted' AFTER error_msg;
