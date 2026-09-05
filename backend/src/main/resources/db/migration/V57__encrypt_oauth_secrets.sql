-- R4-1.28 凭据安全：OAuth client_secret / 第三方 appSecret 改为 AES-GCM 加密落库（"enc:" 前缀 base64 密文）。
-- 密文长度膨胀（~128 字符明文 → ~208 字符密文；~255 → ~377），列宽提升至 512 容纳。
-- 存量明文数据无需迁移：SecretCipher.decrypt 对无前缀值原样放行，下次保存时自动升级为密文。
ALTER TABLE sys_oauth_client MODIFY COLUMN client_secret VARCHAR(512) NOT NULL COMMENT 'client_secret（AES-GCM 加密存储）';
ALTER TABLE sys_oauth_config MODIFY COLUMN app_secret VARCHAR(512) NOT NULL COMMENT '客户端密钥（AES-GCM 加密存储）';
