package cn.admin.scaffold.module.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totpService = new TotpService("test-encryption-key", new MockEnvironment());

    @Test
    void generateSecretAndEncryptDecrypt() {
        String secret = totpService.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.length() >= 16);

        String encrypted = totpService.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, totpService.decrypt(encrypted));
    }

    @Test
    void verifyRejectsBlankCode() {
        assertFalse(totpService.verify("SECRET", ""));
    }

    @Test
    void rejectsDevDefaultKeyOutsideDevProfile() {
        // 非 dev 环境（MockEnvironment 默认无 active profile）下使用 dev 兜底密钥必须启动失败
        assertThrows(IllegalStateException.class,
                () -> new TotpService("dev-only-totp-encryption-key-please-override", new MockEnvironment()));
    }

    @Test
    void allowsDevDefaultKeyInDevProfile() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        TotpService devTotp = new TotpService("dev-only-totp-encryption-key-please-override", dev);
        assertTrue(devTotp.generateSecret().length() >= 16);
    }
}
