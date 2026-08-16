-- 渠道发送记录 PII 防护（批次11，R4-1.38）
--
-- 背景：sys_channel_log.content/target 此前明文落库（验证码/手机号/邮箱等 PII），
-- DB 泄露即用户敏感信息泄露。批11 起发送路径把 target/content 用 SecretCipher
-- （AES-256-GCM，"enc:" 前缀 + base64）加密后写入，回显时解密。
--
-- 列长原因：密文比明文膨胀约 1.33 倍（base64）+ IV/tag 开销。content VARCHAR(4000)
-- 明文上限 4000 字符，UTF-8 最坏 12000 字节，加密后 base64 约 16KB，必须改 TEXT；
-- target VARCHAR(500) 加密后最坏 ~2KB，扩到 VARCHAR(2048)。
--
-- 存量明文行无需迁移改写：回显时非 "enc:" 前缀的值按 fail-closed 统一打码，不再展示。
ALTER TABLE sys_channel_log
    MODIFY COLUMN target VARCHAR(2048) COMMENT '接收目标（敏感值加密落库）',
    MODIFY COLUMN content TEXT COMMENT '内容（敏感值加密落库）';
