ALTER TABLE ai_service_config
    ADD COLUMN daily_limit INT NOT NULL DEFAULT 1000 AFTER enabled;
