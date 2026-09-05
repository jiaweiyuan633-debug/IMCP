ALTER TABLE sys_alert_rule
    ADD COLUMN severity VARCHAR(20) NOT NULL DEFAULT 'WARNING' AFTER enabled,
    ADD COLUMN silence_minutes INT NOT NULL DEFAULT 10 AFTER severity,
    ADD COLUMN webhook_url VARCHAR(500) NULL AFTER silence_minutes;
