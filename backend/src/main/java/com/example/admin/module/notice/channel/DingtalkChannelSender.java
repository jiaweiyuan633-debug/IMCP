package com.example.admin.module.notice.channel;

import com.example.admin.common.LogMaskUtils;
import com.example.admin.module.notice.ChannelType;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 钉钉群机器人渠道：POST 到 webhook，可选加签（secret）。
 * config_json: {"webhook","secret?"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingtalkChannelSender implements MessageChannelSender {

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();

    @Override
    public ChannelType supports() {
        return ChannelType.DINGTALK;
    }

    @Override
    public String send(SysChannelConfigDO config, String target, String title, String content) {
        try {
            JsonNode node = objectMapper.readTree(config.getConfigJson());
            String webhook = node.path("webhook").asText();
            String secret = node.path("secret").asText("");
            String uri = webhook;
            if (!secret.isEmpty()) {
                String timestamp = String.valueOf(System.currentTimeMillis());
                String stringToSign = timestamp + "\n" + secret;
                String sign = sign(secret, stringToSign);
                uri = UriComponentsBuilder.fromUriString(webhook)
                        .queryParam("timestamp", timestamp)
                        .queryParam("sign", sign)
                        .build()
                        .toUriString();
            }
            Map<String, Object> body = Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", title + "\n" + content));
            String response = restClient.post().uri(uri).body(body).retrieve().body(String.class);
            return checkSuccess(response);
        } catch (Exception e) {
            // 批8d：加签模式下请求 URI 携带 sign（HMAC 签名），异常消息内嵌 URI 会泄漏签名，落库/日志前统一打码
            String error = LogMaskUtils.sanitize(e.getMessage());
            log.warn("钉钉发送失败: err={}", error);
            return error;
        }
    }

    private String sign(String secret, String stringToSign) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
    }

    private String checkSuccess(String response) throws Exception {
        if (response == null) {
            return "钉钉无响应";
        }
        JsonNode node = objectMapper.readTree(response);
        return node.path("errcode").asInt(-1) == 0 ? null : "钉钉返回: " + node.path("errmsg").asText(response);
    }
}
