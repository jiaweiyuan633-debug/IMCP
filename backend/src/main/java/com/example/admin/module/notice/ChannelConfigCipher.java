package com.example.admin.module.notice;

import com.example.admin.common.LogMaskUtils;
import com.example.admin.common.SecretCipher;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 渠道配置敏感字段加解密（R4-1.37 批次10）。
 *
 * <p>sys_channel_config.config_json 此前整体明文落库（SMTP 密码、短信网关 apiKey、钉钉加签
 * secret 等），虽然批8 已在回显打码、日志脱敏，但数据库泄露即密钥泄露。本组件按
 * {@link LogMaskUtils} 的敏感键清单对 config_json 中命中的字段值做 AES-256-GCM 加密
 * （{@link SecretCipher}，"enc:" 前缀），实现「回显打码的字段 = 落库加密的字段」，两套语义天然对齐：
 * <ul>
 *   <li>保存：{@link #encryptConfig} 在打码占位合并之后加密，enc: 前缀幂等跳过（编辑未改动
 *       敏感值时，mergeMasked 补回的密文不会被重复加密）；</li>
 *   <li>发送：{@link #decryptConfigOf} 在交给渠道 sender 前解密为明文，sender 无需感知密文；</li>
 *   <li>回显：直接复用 {@link LogMaskUtils#maskStructuredConfig}——密文键命中敏感清单被整体打码，
 *       无需先解密，前端仍见 ****** 占位。</li>
 * </ul>
 *
 * <p>边界：webhook/url 等地址字段不加密（回显需保留供前端编辑，与批8 打码语义一致），
 * 其 URL 内携带的 access_token/sign 由 {@link LogMaskUtils#sanitize} 在日志侧脱敏。
 * JSON 非法或加密失败时原样放行（保持既有行为，发送路径由 sender 校验报错）。
 */
@Service
@RequiredArgsConstructor
public class ChannelConfigCipher {

    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;

    /** 对 config_json 中敏感字段值加密；null/空串/非法 JSON 原样返回；enc: 前缀跳过（幂等）。 */
    public String encryptConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            encryptFields(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return json;
        }
    }

    /** 对 config_json 中 enc: 密文值解密为明文；非 enc: 前缀原样保留（幂等）；解密失败置 null（fail-closed）。 */
    public String decryptConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            decryptFields(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return json;
        }
    }

    /** 返回 configJson 被解密的新 DO（发送路径）；config 为空或 configJson 为空时原样返回。 */
    public SysChannelConfigDO decryptConfigOf(SysChannelConfigDO config) {
        if (config == null || !StringUtils.hasText(config.getConfigJson())) {
            return config;
        }
        SysChannelConfigDO plain = new SysChannelConfigDO();
        BeanUtils.copyProperties(config, plain);
        plain.setConfigJson(decryptConfig(config.getConfigJson()));
        return plain;
    }

    /** 递归：命中敏感键且未加密的文本值加密；其余节点递归。 */
    private void encryptFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (LogMaskUtils.isSensitiveField(entry.getKey())
                        && value.isTextual()
                        && !secretCipher.isEncrypted(value.asText())) {
                    objectNode.set(entry.getKey(), TextNode.valueOf(secretCipher.encrypt(value.asText())));
                } else {
                    encryptFields(value);
                }
            });
        } else {
            node.elements().forEachRemaining(this::encryptFields);
        }
    }

    /** 递归：enc: 前缀的文本值解密；其余节点递归。 */
    private void decryptFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual() && secretCipher.isEncrypted(value.asText())) {
                    String plain = secretCipher.decrypt(value.asText());
                    objectNode.set(entry.getKey(), plain == null ? NullNode.instance : TextNode.valueOf(plain));
                } else {
                    decryptFields(value);
                }
            });
        } else {
            node.elements().forEachRemaining(this::decryptFields);
        }
    }
}
