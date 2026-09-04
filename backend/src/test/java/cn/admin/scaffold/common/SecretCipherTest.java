package cn.admin.scaffold.common;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R4-1.28：OAuth 凭据加解密组件。断言 AES-GCM 往返一致、"enc:" 前缀识别、
 * 存量明文原样放行（避免强制数据迁移）、篡改密文 fail-closed、生产禁用开发默认密钥。
 */
class SecretCipherTest {

    private SecretCipher cipher() {
        return new SecretCipher("test-key", devEnv());
    }

    private Environment devEnv() {
        Environment env = mock(Environment.class);
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        return env;
    }

    @Test
    void encryptDecryptRoundTrip() {
        SecretCipher cipher = cipher();
        String encrypted = cipher.encrypt("my-secret");
        assertThat(encrypted).startsWith("enc:");
        assertThat(cipher.isEncrypted(encrypted)).isTrue();
        assertThat(cipher.decrypt(encrypted)).isEqualTo("my-secret");
    }

    @Test
    void encryptIsRandomizedPerCall() {
        SecretCipher cipher = cipher();
        // GCM 每次随机 IV：同一明文两次加密必须产生不同密文，避免同值碰撞侧信道
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void decryptPassesThroughLegacyPlaintext() {
        SecretCipher cipher = cipher();
        // 无前缀视为存量明文（种子/历史数据），原样放行直到下次保存自动升级为密文
        assertThat(cipher.decrypt("legacy-plain-secret")).isEqualTo("legacy-plain-secret");
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("")).isNull();
        assertThat(cipher.isEncrypted("legacy-plain-secret")).isFalse();
    }

    @Test
    void decryptFailsClosedOnTamperedCiphertext() {
        SecretCipher cipher = cipher();
        String tampered = cipher.encrypt("my-secret");
        String corrupted = tampered.substring(0, tampered.length() - 1) + "X";
        // 篡改密文/换密钥场景：解密失败必须返回 null（校验恒失败），绝不回退明文
        assertThat(cipher.decrypt(corrupted)).isNull();
    }

    @Test
    void refusesDevFallbackKeyOutsideDevProfile() {
        Environment prodEnv = mock(Environment.class);
        when(prodEnv.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        assertThrows(IllegalStateException.class,
                () -> new SecretCipher("dev-only-totp-encryption-key-please-override", prodEnv));
    }

    @Test
    void refusesBlankKey() {
        assertThrows(IllegalStateException.class, () -> new SecretCipher("", devEnv()));
    }
}
