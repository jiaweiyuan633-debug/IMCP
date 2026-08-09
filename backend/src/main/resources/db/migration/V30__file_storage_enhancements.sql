ALTER TABLE sys_file
    ADD COLUMN content_type VARCHAR(200) NULL AFTER original_name,
    ADD COLUMN category VARCHAR(50) NULL AFTER content_type,
    ADD COLUMN sha256 CHAR(64) NULL AFTER size,
    ADD COLUMN scan_status VARCHAR(20) NOT NULL DEFAULT 'SKIPPED' AFTER sha256,
    ADD COLUMN scan_message VARCHAR(500) NULL AFTER scan_status,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER updated_at;

CREATE INDEX idx_sys_file_tenant_created ON sys_file (tenant_id, created_at);
CREATE INDEX idx_sys_file_sha256 ON sys_file (sha256);
