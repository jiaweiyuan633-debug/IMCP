ALTER TABLE sys_user
    ADD COLUMN updated_by BIGINT NULL AFTER created_by,
    ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER updated_by;

CREATE TABLE sys_notice_read (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notice_id BIGINT NOT NULL,
    read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_notice_read (user_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='notice read';

CREATE TABLE sys_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    url VARCHAR(500) NOT NULL,
    size BIGINT NOT NULL DEFAULT 0,
    storage_type VARCHAR(20) NOT NULL DEFAULT 'local',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='file metadata';

