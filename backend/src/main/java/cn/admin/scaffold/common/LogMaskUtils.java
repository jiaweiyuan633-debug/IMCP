package cn.admin.scaffold.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 日志脱敏工具（批8c/8d 增强）。
 *
 * <p>统一负责三类敏感信息抹除，供操作日志、字段审计快照、错误消息与渠道配置回显共用：
 * <ol>
 *   <li>对象字段黑名单：精确键名命中即整值打码（{@link #SENSITIVE_FIELDS}），覆盖 OAuth
 *       appSecret/clientSecret、通知渠道 configJson、验证码、token 等；</li>
 *   <li>内嵌 JSON 递归脱敏：TextNode 值为 JSON 字符串时解析后递归打码，避免
 *       {@code {"config":{"secret":"x"}}} 这类"字段名不进黑名单、值却是 JSON"的漏网；</li>
 *   <li>URL 凭证脱敏：查询串中的敏感参数（token/sign/secret 等）与 basic-auth 密码一律打码，
 *       防止带签名/令牌的完整 URL 经异常消息或日志泄漏（{@link #sanitize}）。</li>
 * </ol>
 */
public final class LogMaskUtils {

    /** 打码占位符：与前端约定的统一掩码。保存侧据此识别"未改动的敏感值"并合并保留原值。 */
    public static final String MASK = "******";

    /** 判断键名是否命中敏感字段黑名单：渠道配置加密与回显打码共用同一清单，防止两套清单漂移。 */
    public static boolean isSensitiveField(String key) {
        return key != null && SENSITIVE_FIELDS_LOWER.contains(key.toLowerCase(Locale.ROOT));
    }

    /** 对象字段黑名单：精确键名匹配，命中即整值打码（Textual 或结构内递归）。 */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "oldPassword", "newPassword",
            "totpCode", "totpSecret", "secret", "secretKey",
            "apiKey", "api_key", "appSecret", "clientSecret",
            // MCP Server authToken 纳入清单：漏配时 @OperLog 新增/编辑服务会把明文令牌落操作日志
            "authToken", "auth_token",
            "accessKeyId", "accessKeySecret",
            "accessToken", "access_token", "refreshToken", "refresh_token",
            "authorization", "authCode", "token",
            "sign", "signature",
            "configValue", "configJson",
            "phone", "mobile", "email", "idCard", "idCardNo");

    /** 小写化副本：敏感键匹配大小写不敏感，覆盖 Webhook 常见的 "Authorization" 等首字母大写键。 */
    private static final Set<String> SENSITIVE_FIELDS_LOWER =
            SENSITIVE_FIELDS.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());

    /** URL 查询参数黑名单：值一律打码，防止 URL 携带的 token/sign/secret 经错误消息或日志泄漏。 */
    private static final Set<String> SENSITIVE_QUERY_PARAMS = Set.of(
            "token", "access_token", "sign", "secret", "api_key", "appkey", "key", "code");

    /** URL 内嵌 basic-auth 凭据：//user:password@host → //user:******@host */
    private static final Pattern URL_USERINFO = Pattern.compile("(//[^/@\\s]+:)[^@\\s]+(@)");

    private LogMaskUtils() {
    }

    /** 全量 JSON 脱敏：序列化 → 递归打码（通用日志/审计快照路径）。失败回退 null。 */
    public static String toMaskedJson(Object value, ObjectMapper objectMapper) {
        return toMaskedJson(value, objectMapper, null);
    }

    /**
     * 全量 JSON 脱敏 + 额外字段整值打码（配合 @OperLog.maskFields）：先按黑名单递归打码，
     * 再对 extraFields 命中的键名（大小写不敏感，数组内对象与嵌套对象均覆盖）整值打码。
     * 用于发送类操作的正文/参数脱敏，避免向黑名单加入 content 等通用键误伤公告/通知审计留痕。
     */
    public static String toMaskedJson(Object value, ObjectMapper objectMapper, String[] extraFields) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            mask(node, objectMapper);
            if (extraFields != null && extraFields.length > 0) {
                Set<String> extras = Arrays.stream(extraFields)
                        .filter(StringUtils::hasText)
                        .map(f -> f.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                maskExtraFields(node, extras);
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * 结构化渠道配置脱敏（toVo 回显路径）：仅对黑名单键打码，保留结构/地址字段
     * （webhook/url/host/username 等）与字段名，保证前端可正常编辑回填；非法 JSON 原样返回。
     *
     * <p>与 {@link #toMaskedJson} 的差异：不打码 URL 查询参数、不解析内嵌 JSON——因为回显结果
     * 会经保存流程回写库，若对地址类字段做部分打码将无法在 {@link #mergeMasked} 中无损还原。
     */
    public static String maskStructuredConfig(String json, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            maskConfig(node);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException | RuntimeException exception) {
            return json;
        }
    }

    /**
     * 打码占位合并（保存路径）：masked 中命中 {@link #MASK} 占位符的叶子值，用 real 同路径值补齐。
     *
     * <p>解决回显→保存回写覆盖真实密钥的问题：前端编辑未改动敏感值（界面显示为 ******），
     * 保存时若直接落库会把真实密钥覆盖为掩码；合并后仅"确系用户新输入"的值才入库。
     * masked 不含占位符或解析失败时原样返回 masked。
     */
    public static String mergeMasked(String masked, String real, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(masked) || !StringUtils.hasText(real) || !masked.contains(MASK)) {
            return masked;
        }
        try {
            ObjectNode target = (ObjectNode) objectMapper.readTree(masked);
            JsonNode source = objectMapper.readTree(real);
            mergePlaceholders(target, source);
            return objectMapper.writeValueAsString(target);
        } catch (JsonProcessingException | RuntimeException exception) {
            return masked;
        }
    }

    /** 通用消息脱敏（异常信息/日志文本）：打码 URL 查询参数与 basic-auth 密码，其余原样保留。 */
    public static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        String result = URL_USERINFO.matcher(text).replaceAll("$1" + MASK + "$2");
        for (String param : SENSITIVE_QUERY_PARAMS) {
            result = result.replaceAll("([?&]" + Pattern.quote(param) + "=)[^&#\\s\"']+", "$1" + MASK);
        }
        return result;
    }

    // ---------- 通用脱敏（含内嵌 JSON / URL 处理） ----------

    private static void mask(JsonNode node, ObjectMapper objectMapper) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (isSensitiveField(entry.getKey())) {
                    objectNode.set(entry.getKey(), maskedValue(value));
                } else if (value.isTextual()) {
                    maskTextualLeaf(objectNode, entry.getKey(), value.asText(), objectMapper);
                } else {
                    mask(value, objectMapper);
                }
            });
        } else {
            node.elements().forEachRemaining(child -> mask(child, objectMapper));
        }
    }

    /** 额外字段打码：命中的键名整值打码，其余节点递归（数组元素、嵌套对象均覆盖）。 */
    private static void maskExtraFields(JsonNode node, Set<String> extraLower) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                if (extraLower.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    objectNode.set(entry.getKey(), TextNode.valueOf(MASK));
                } else {
                    maskExtraFields(entry.getValue(), extraLower);
                }
            });
        } else {
            node.elements().forEachRemaining(child -> maskExtraFields(child, extraLower));
        }
    }

    /** 非敏感文本叶子：内嵌 JSON 递归脱敏；URL 打码敏感查询参数；普通文本原样保留。 */
    private static void maskTextualLeaf(ObjectNode parent, String key, String text, ObjectMapper objectMapper) {
        if (looksLikeJson(text)) {
            parent.set(key, maskEmbeddedJson(text, objectMapper));
        } else if (text.contains("://")) {
            parent.set(key, TextNode.valueOf(sanitize(text)));
        }
    }

    private static JsonNode maskEmbeddedJson(String text, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.readTree(text);
            mask(node, objectMapper);
            return TextNode.valueOf(node.toString());
        } catch (JsonProcessingException exception) {
            return TextNode.valueOf(text);
        }
    }

    // ---------- 结构化配置脱敏（仅黑名单整值打码，地址字段原样保留） ----------

    private static void maskConfig(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (isSensitiveField(entry.getKey())) {
                    objectNode.set(entry.getKey(), maskedValue(value));
                } else {
                    maskConfig(value);
                }
            });
        } else {
            node.elements().forEachRemaining(LogMaskUtils::maskConfig);
        }
    }

    private static void mergePlaceholders(ObjectNode target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        target.fields().forEachRemaining(entry -> {
            JsonNode targetValue = entry.getValue();
            JsonNode sourceValue = source.get(entry.getKey());
            if (targetValue.isObject() && sourceValue != null) {
                mergePlaceholders((ObjectNode) targetValue, sourceValue);
            } else if (targetValue.isTextual() && MASK.equals(targetValue.asText()) && sourceValue != null) {
                target.set(entry.getKey(), sourceValue.deepCopy());
            }
        });
    }

    /** 敏感键：Textual/容器值整值打码，null 保留（避免改变结构语义）。 */
    private static JsonNode maskedValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return value;
        }
        return TextNode.valueOf(MASK);
    }

    private static boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
