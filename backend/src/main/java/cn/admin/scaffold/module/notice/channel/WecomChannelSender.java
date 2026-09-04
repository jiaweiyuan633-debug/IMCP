package cn.admin.scaffold.module.notice.channel;

import cn.admin.scaffold.common.LogMaskUtils;
import cn.admin.scaffold.module.notice.ChannelType;
import cn.admin.scaffold.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 企业微信群机器人渠道：POST 到 webhook。
 * config_json: {"webhook"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WecomChannelSender implements MessageChannelSender {

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();

    @Override
    public ChannelType supports() {
        return ChannelType.WECOM;
    }

    @Override
    public String send(SysChannelConfigDO config, String target, String title, String content) {
        try {
            JsonNode node = objectMapper.readTree(config.getConfigJson());
            Map<String, Object> body = Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", title + "\n" + content));
            String response = restClient.post()
                    .uri(node.path("webhook").asText())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return checkSuccess(response);
        } catch (Exception e) {
            // 批8d：异常消息可能内嵌请求 URL（webhook 地址携带的查询凭证），落库/日志前统一打码
            String error = LogMaskUtils.sanitize(e.getMessage());
            log.warn("企微发送失败: err={}", error);
            return error;
        }
    }

    private String checkSuccess(String response) throws Exception {
        if (response == null) {
            return "企业微信无响应";
        }
        JsonNode node = objectMapper.readTree(response);
        return node.path("errcode").asInt(-1) == 0 ? null : "企业微信返回: " + node.path("errmsg").asText(response);
    }
}
