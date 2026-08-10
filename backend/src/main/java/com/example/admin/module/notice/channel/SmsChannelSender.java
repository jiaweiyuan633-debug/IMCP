package com.example.admin.module.notice.channel;

import com.example.admin.module.notice.ChannelType;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信渠道：POST JSON 到可配置的短信网关。
 * config_json: {"url","apiKey","signName","templateId"}
 * 网关约定：入参 {apiKey,signName,templateId,phones[],content}，响应 JSON 含 code=0 表示成功。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsChannelSender implements MessageChannelSender {

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();

    @Override
    public ChannelType supports() {
        return ChannelType.SMS;
    }

    @Override
    public String send(SysChannelConfigDO config, String target, String title, String content) {
        try {
            JsonNode node = objectMapper.readTree(config.getConfigJson());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("apiKey", node.path("apiKey").asText());
            body.put("signName", node.path("signName").asText());
            body.put("templateId", node.path("templateId").asText());
            body.put("phones", List.of(target.split("[,;，；]")));
            body.put("content", content);
            String response = restClient.post()
                    .uri(node.path("url").asText())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return checkSuccess(response);
        } catch (Exception e) {
            log.warn("短信发送失败: target={}, err={}", target, e.getMessage());
            return e.getMessage();
        }
    }

    /** 网关成功约定：JSON 含 code=0，或纯文本以 0: 前缀开头。 */
    private String checkSuccess(String response) {
        if (response == null) {
            return "短信网关无响应";
        }
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.path("code").asInt(-1) == 0) {
                return null;
            }
            return "短信网关返回: " + node.path("message").asText(response);
        } catch (Exception ignored) {
            return response.trim().startsWith("0:") ? null : "短信网关返回: " + response;
        }
    }
}
