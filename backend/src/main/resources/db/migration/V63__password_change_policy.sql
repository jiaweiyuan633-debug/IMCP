-- =====================================================================
-- V63：密码策略字段（批次1·安全阻断）
-- 1. must_change_password：标记"首次登录必须改密"的用户（默认口令种子）
-- 2. password_changed_at：上次改密时间，供"密码过期强制改密"策略使用
-- 3. 将仍使用默认种子哈希（admin123）的存量账号标记为必须改密
--    （生产由 SecurityProperties.forcePasswordChange + 登录逻辑强制执行，
--     本地 dev/test 默认不强制，保持 admin/admin123 可登录的开发体验）
-- =====================================================================

ALTER TABLE sys_user
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '首次登录必须改密：1=是 0=否',
    ADD COLUMN password_changed_at DATETIME NULL COMMENT '上次改密时间（密码过期策略依据）';

-- 存量默认口令账号（V1 种子哈希，即 admin123）标记为必须改密
UPDATE sys_user
SET must_change_password = 1
WHERE password = '$2a$10$46ip4ZqIPk1MncJM9QDzCOxPKNc/N6RzWcMxgI9XQfgspPWLMFGzK';
