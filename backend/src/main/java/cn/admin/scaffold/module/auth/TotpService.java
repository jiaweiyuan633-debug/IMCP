package cn.admin.scaffold.module.auth;

import cn.hutool.core.codec.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int WINDOW = 1;

    /** 与 application-dev.yml 一致的开发兜底密钥，仅允许在 dev profile 下使用 */
    private static final String DEV_FALLBACK_KEY = "dev-only-totp-encryption-key-please-override";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] encryptionKey;

    public TotpService(@Value("${app.totp.encryption-key:}") String key, Environment environment) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("TOTP 加密密钥未配置，请通过环境变量 TOTP_ENCRYPTION_KEY 注入（不得复用 JWT_SECRET）");
        }
        boolean isDev = environment != null && environment.acceptsProfiles(Profiles.of("dev"));
        if (!isDev && DEV_FALLBACK_KEY.equals(key)) {
            throw new IllegalStateException("生产环境禁止使用开发默认 TOTP 密钥，请通过环境变量 TOTP_ENCRYPTION_KEY 注入独立密钥");
        }
        try {
            this.encryptionKey = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP encryption key init failed", exception);
        }
    }

    public String generateSecret() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return Base32.encode(bytes).replace("=", "");
    }

    public String buildOtpauthUrl(String secret, String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        return "otpauth://totp/智能管理平台:" + encoded + "?secret=" + secret + "&issuer=智能管理平台";
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }
        long counter = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            if (constantTimeEquals(generateCode(secret, counter + offset), code.trim())) {
                return true;
            }
        }
        return false;
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP secret encryption failed", exception);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[12];
            if (combined.length < iv.length) {
                return stored;
            }
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return stored;
        }
    }

    private String generateCode(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(Base32.decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", otp);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP generation failed", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
