package com.example.admin.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskUtilsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void masksSensitiveFieldsRecursively() throws Exception {
        Payload payload = new Payload("admin", "plain-password", "real@example.com", "13800138000", Map.of("apiKey", "secret-key"));

        String json = LogMaskUtils.toMaskedJson(payload, objectMapper);

        assertTrue(json.contains("\"password\":\"******\""));
        assertTrue(json.contains("\"email\":\"******\""));
        assertTrue(json.contains("\"phone\":\"******\""));
        assertTrue(json.contains("\"apiKey\":\"******\""));
        assertFalse(json.contains("plain-password"));
        assertFalse(json.contains("secret-key"));
        assertEquals("admin", objectMapper.readTree(json).get("username").asText());
    }

    // ---------- 批8c/8d：内嵌 JSON 递归脱敏 + 黑名单扩充 + 结构化配置脱敏 + 占位合并 + URL 凭证脱敏 ----------

    /** 字段值为 JSON 字符串时递归解析脱敏，结构保留、敏感值打码（此前整段原样泄漏）。 */
    @Test
    void masksSensitiveKeysInsideEmbeddedJsonString() throws Exception {
        Payload payload = new Payload("admin", "plain-password", "real@example.com", "13800138000",
                Map.of("detail", "{\"secret\":\"abc\",\"webhook\":\"https://x.com/hook\"}"));

        String json = LogMaskUtils.toMaskedJson(payload, objectMapper);

        // 序列化时内嵌 JSON 的引号被转义，直接对解析后的 detail 字符串值断言
        String detail = objectMapper.readTree(json).path("nested").path("detail").asText();
        assertFalse(detail.contains("abc"));
        assertTrue(detail.contains("\"secret\":\"******\""));
        assertTrue(detail.contains("\"webhook\":\"https://x.com/hook\""));
    }

    /** 新增黑名单键（OAuth 凭据、通知渠道密钥、MCP 令牌）整值打码。 */
    @Test
    void masksNewlyAddedSensitiveFields() {
        Payload payload = new Payload("admin", "plain-password", "real@example.com", "13800138000",
                Map.of("appSecret", "s1", "clientSecret", "s2", "secretKey", "s3", "configValue", "s4",
                        // R4-1.41：MCP Server authToken（批13 加密落库的凭据）操作日志回显必须打码
                        "authToken", "mcp-bearer-token"));

        String json = LogMaskUtils.toMaskedJson(payload, objectMapper);

        assertTrue(json.contains("\"appSecret\":\"******\""));
        assertTrue(json.contains("\"clientSecret\":\"******\""));
        assertTrue(json.contains("\"secretKey\":\"******\""));
        assertTrue(json.contains("\"configValue\":\"******\""));
        assertTrue(json.contains("\"authToken\":\"******\""));
        assertFalse(json.contains("s1"));
        assertFalse(json.contains("s2"));
        assertFalse(json.contains("s3"));
        assertFalse(json.contains("s4"));
        assertFalse(json.contains("mcp-bearer-token"));
    }

    /** 结构化配置脱敏：保留地址/账号字段供回显编辑，仅对密钥键打码。 */
    @Test
    void maskStructuredConfigKeepsAddressFieldsAndMasksSecrets() {
        String masked = LogMaskUtils.maskStructuredConfig(
                "{\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=real-token\","
                        + "\"secret\":\"real-secret\",\"username\":\"ops\"}", objectMapper);

        assertTrue(masked.contains("\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=real-token\""));
        assertTrue(masked.contains("\"username\":\"ops\""));
        assertTrue(masked.contains("\"secret\":\"******\""));
        assertFalse(masked.contains("real-secret"));
    }

    /** 非法 JSON 原样返回，不影响既有渠道配置展示。 */
    @Test
    void maskStructuredConfigReturnsRawOnInvalidJson() {
        assertEquals("not-json", LogMaskUtils.maskStructuredConfig("not-json", objectMapper));
        assertEquals(null, LogMaskUtils.maskStructuredConfig(null, objectMapper));
    }

    /** 打码占位合并：未改动的敏感值（******）用库中原值补齐，其余保留请求值。 */
    @Test
    void mergeMaskedRestoresPlaceholderFromReal() {
        String masked = "{\"webhook\":\"https://x/hook\",\"secret\":\"******\",\"name\":\"渠道A\"}";
        String real = "{\"webhook\":\"https://x/hook\",\"secret\":\"real-secret\",\"name\":\"渠道B\"}";

        String merged = LogMaskUtils.mergeMasked(masked, real, objectMapper);

        assertTrue(merged.contains("\"secret\":\"real-secret\""));
        assertTrue(merged.contains("\"webhook\":\"https://x/hook\""));
        assertTrue(merged.contains("\"name\":\"渠道A\""));
        assertFalse(merged.contains("******"));
    }

    /** 无占位符时不做合并，直接采用请求值（新密钥）。 */
    @Test
    void mergeMaskedReturnsAsIsWithoutPlaceholder() {
        String masked = "{\"secret\":\"brand-new\"}";
        assertEquals(masked, LogMaskUtils.mergeMasked(masked, "{\"secret\":\"old\"}", objectMapper));
    }

    /** 通用消息脱敏：打码 URL 查询凭证与 basic-auth 密码，保留其余文本。 */
    @Test
    void sanitizeMasksSensitiveQueryParamsAndUserInfoPassword() {
        String result = LogMaskUtils.sanitize(
                "connect to https://user:pass@example.com/hook?token=abc&sign=xyz&foo=bar failed");

        assertTrue(result.contains("https://user:******@example.com/hook"));
        assertTrue(result.contains("token=******"));
        assertTrue(result.contains("sign=******"));
        assertTrue(result.contains("foo=bar"));
        assertFalse(result.contains("pass@"));
        assertFalse(result.contains("abc"));
        assertFalse(result.contains("xyz"));
    }

    /** 无 URL 的普通错误文本原样保留。 */
    @Test
    void sanitizeIgnoresPlainText() {
        assertEquals("SMTP timeout", LogMaskUtils.sanitize("SMTP timeout"));
    }

    /** R4-1.37：敏感键判定（渠道配置加密与回显打码共用同一清单）。 */
    @Test
    void isSensitiveFieldMatchesBlacklistKeysOnly() {
        assertTrue(LogMaskUtils.isSensitiveField("password"));
        assertTrue(LogMaskUtils.isSensitiveField("secret"));
        assertTrue(LogMaskUtils.isSensitiveField("apiKey"));
        assertTrue(LogMaskUtils.isSensitiveField("authToken"));
        assertTrue(LogMaskUtils.isSensitiveField("Authorization"));
        assertFalse(LogMaskUtils.isSensitiveField("host"));
        assertFalse(LogMaskUtils.isSensitiveField("webhook"));
        assertFalse(LogMaskUtils.isSensitiveField(null));
    }

    // ---------- R4-1.38：@OperLog.maskFields 额外字段整值打码 ----------

    /** maskFields 命中的键（大小写不敏感、嵌套覆盖）整值打码，其余字段保留。 */
    @Test
    void toMaskedJsonWithExtraFieldsMasksDeclaredKeys() throws Exception {
        Payload payload = new Payload("admin", "plain-password", "real@example.com", "13800138000",
                Map.of("content", "您的验证码是 123456", "target", "a@example.com"));

        String json = LogMaskUtils.toMaskedJson(payload, objectMapper, new String[]{"content", "TARGET"});

        // target 大小写不敏感命中；content 不在黑名单但被 maskFields 精确打码
        assertTrue(json.contains("\"content\":\"******\""));
        assertTrue(json.contains("\"target\":\"******\""));
        assertFalse(json.contains("123456"));
        assertFalse(json.contains("a@example.com"));
        assertEquals("admin", objectMapper.readTree(json).get("username").asText());
    }

    /** 无 maskFields（null/空数组）时行为与旧版一致，不加额外打码。 */
    @Test
    void toMaskedJsonWithoutExtraFieldsKeepsPriorBehavior() {
        Payload payload = new Payload("admin", "plain-password", "real@example.com", "13800138000",
                Map.of("content", "公告正文", "target", "全员"));

        String json = LogMaskUtils.toMaskedJson(payload, objectMapper, new String[0]);

        assertTrue(json.contains("\"content\":\"公告正文\""));
        assertTrue(json.contains("\"target\":\"全员\""));
    }

    @Data
    @AllArgsConstructor
    private static class Payload {
        private String username;
        private String password;
        private String email;
        private String phone;
        private Map<String, String> nested;
    }
}
