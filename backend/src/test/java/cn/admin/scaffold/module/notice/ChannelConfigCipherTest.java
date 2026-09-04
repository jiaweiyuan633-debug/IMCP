package cn.admin.scaffold.module.notice;

import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 渠道配置敏感字段加解密（R4-1.37 批次10）：
 * 命中敏感清单的字段值加密、enc: 前缀幂等、发送前解密、非法 JSON 原样放行。
 */
class ChannelConfigCipherTest {

    private ChannelConfigCipher cipher;
    private SecretCipher secretCipher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        secretCipher = new SecretCipher("unit-test-encryption-key-not-for-prod", null);
        cipher = new ChannelConfigCipher(secretCipher, objectMapper);
    }

    /** 加密：MAIL 场景 password 加密，地址/账号字段保留明文。 */
    @Test
    void encryptEncryptsSensitiveValuesOnly() {
        String encrypted = cipher.encryptConfig(
                "{\"host\":\"smtp.example.com\",\"port\":465,\"username\":\"u\",\"password\":\"p@ss\"}");

        assertThat(encrypted)
                .contains("\"host\":\"smtp.example.com\"")
                .contains("\"port\":465")
                .contains("\"username\":\"u\"")
                .contains("\"password\":\"enc:")
                .doesNotContain("p@ss");
    }

    /** 加密幂等：已是 enc: 密文的值跳过，不会重复加密。 */
    @Test
    void encryptIsIdempotentForAlreadyEncryptedValues() {
        String once = cipher.encryptConfig("{\"secret\":\"real-secret\"}");
        String twice = cipher.encryptConfig(once);

        assertThat(twice).isEqualTo(once);
    }

    /** 解密：enc: 密文还原为明文，非敏感字段保持原样。 */
    @Test
    void decryptRestoresPlaintext() {
        String encrypted = cipher.encryptConfig("{\"secret\":\"real-secret\",\"host\":\"x.com\"}");

        String decrypted = cipher.decryptConfig(encrypted);

        assertThat(decrypted)
                .contains("\"secret\":\"real-secret\"")
                .contains("\"host\":\"x.com\"");
    }

    /** 解密幂等：无 enc: 前缀的值原样保留。 */
    @Test
    void decryptKeepsNonEncryptedValuesUntouched() {
        String json = "{\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=tok\",\"secret\":\"plain\"}";

        assertThat(cipher.decryptConfig(json)).isEqualTo(json);
    }

    /** 嵌套对象（Webhook headers）中的敏感键同样加密/解密。 */
    @Test
    void encryptAndDecryptNestedObjects() {
        String json = "{\"url\":\"https://example.com/hook\","
                + "\"headers\":{\"Authorization\":\"Bearer tok\",\"X-Custom\":\"v\"}}";

        String encrypted = cipher.encryptConfig(json);

        assertThat(encrypted)
                .contains("\"Authorization\":\"enc:")
                .doesNotContain("Bearer tok")
                .contains("\"X-Custom\":\"v\"");
        assertThat(cipher.decryptConfig(encrypted)).contains("\"Authorization\":\"Bearer tok\"");
    }

    /** decryptConfigOf：发送路径返回 configJson 解密的新 DO，其余字段原样拷贝。 */
    @Test
    void decryptConfigOfReturnsDecryptedCopy() {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setId(7L);
        config.setChannelType("MAIL");
        config.setConfigJson("{\"password\":\"" + secretCipher.encrypt("p@ss") + "\",\"host\":\"h\"}");

        SysChannelConfigDO plain = cipher.decryptConfigOf(config);

        assertThat(plain).isNotSameAs(config);
        assertThat(plain.getId()).isEqualTo(7L);
        assertThat(plain.getChannelType()).isEqualTo("MAIL");
        assertThat(plain.getConfigJson()).contains("\"password\":\"p@ss\"").doesNotContain("enc:");
    }

    /** null / 空串 / 非法 JSON：原样放行，不抛异常。 */
    @Test
    void malformedInputPassedThrough() {
        assertThat(cipher.encryptConfig(null)).isNull();
        assertThat(cipher.decryptConfig("  ")).isEqualTo("  ");
        assertThat(cipher.encryptConfig("{not-json")).isEqualTo("{not-json");
        assertThat(cipher.decryptConfigOf(null)).isNull();
    }
}
