package cn.admin.scaffold.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 应用级敏感凭据加解密（AES-256-GCM，12 字节 IV 前置，base64 后带 "enc:" 前缀落库）。
 *
 * <p>与 {@code TotpService} 共用同一个 {@code app.totp.encryption-key}：Helm 已对
 * TOTP_ENCRYPTION_KEY fail-fast，复用即保证 OAuth client_secret / 第三方 appSecret 的
 * 落库加密密钥同样"缺失即启动失败"，不新增部署密钥、不动 K8s Secret 结构。
 *
 * <p>兼容策略：{@link #decrypt} 对 "enc:" 前缀密文解密，解密失败返回 null（fail-closed）；
 * 无前缀的存量明文（种子/历史数据）直接原样放行，避免一次全量数据迁移，并在下次保存时
 * 由服务层自动升级为密文。
 */
@Service
public class SecretCipher {

    private static final String PREFIX = "enc:";

    /** 与 application-dev.yml 一致的开发兜底密钥，仅允许在 dev profile 下使用 */
    private static final String DEV_FALLBACK_KEY = "dev-only-totp-encryption-key-please-override";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] key;

    public SecretCipher(@Value("${app.totp.encryption-key:}") String encryptionKey, Environment environment) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("应用凭据加密密钥未配置，请通过环境变量 TOTP_ENCRYPTION_KEY 注入（不得复用 JWT_SECRET）");
        }
        boolean isDev = environment != null && environment.acceptsProfiles(Profiles.of("dev"));
        if (!isDev && DEV_FALLBACK_KEY.equals(encryptionKey)) {
            throw new IllegalStateException("生产环境禁止使用开发默认应用密钥，请通过环境变量 TOTP_ENCRYPTION_KEY 注入独立密钥");
        }
        try {
            this.key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SecretCipher key init failed", exception);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SecretCipher encryption failed", exception);
        }
    }

    /** 解密密文；null/空串返回 null，"enc:" 密文解密失败返回 null（fail-closed），无前缀视为存量明文原样返回。 */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (combined.length < 12) {
                return null;
            }
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return null;
        }
    }

    /** 判断存量值是否已是密文（"enc:" 前缀）。用于保存路径识别"占位/不变"与"待加密明文"。 */
    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
