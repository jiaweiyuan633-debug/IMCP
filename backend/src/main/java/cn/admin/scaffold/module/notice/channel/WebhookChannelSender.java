package cn.admin.scaffold.module.notice.channel;

import cn.admin.scaffold.common.LogMaskUtils;
import cn.admin.scaffold.common.SsrfUrlValidator;
import cn.admin.scaffold.module.notice.ChannelType;
import cn.admin.scaffold.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 Webhook 渠道：向任意 URL 推送 JSON 消息，适用于各类自建机器人/网关。
 *
 * <p>config_json 约定：
 * <pre>{@code
 * {
 *   "url": "https://example.com/hook",
 *   "method": "POST",                                  // 可选，默认 POST，支持 POST/PUT/PATCH
 *   "headers": { "Authorization": "Bearer xxx" }       // 可选，自定义请求头
 * }
 * }</pre>
 * 请求体固定为 {@code {"target": ..., "title": ..., "content": ...}}，2xx 视为发送成功。
 */
@Slf4j
@Component
public class WebhookChannelSender implements MessageChannelSender {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public WebhookChannelSender(ObjectMapper objectMapper) {
        this(objectMapper, RestClient.builder().build());
    }

    /** 测试/自定义注入入口：可换用 mock 的 RestClient 验证请求构造。 */
    WebhookChannelSender(ObjectMapper objectMapper, RestClient restClient) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public ChannelType supports() {
        return ChannelType.WEBHOOK;
    }

    @Override
    public String send(SysChannelConfigDO config, String target, String title, String content) {
        try {
            JsonNode node = objectMapper.readTree(config.getConfigJson());
            String url = node.path("url").asText();
            if (!StringUtils.hasText(url)) {
                return "Webhook URL 未配置";
            }
            // 与告警 Webhook 同源 SSRF 防护，发送前按"静态+DNS 解析"复核，拒绝内网目标。
            String error = SsrfUrlValidator.validateOutboundHttpUrlWithDns(url);
            if (error != null) {
                return "Webhook URL 不合法: " + error;
            }
            HttpMethod method = parseMethod(node.path("method").asText("POST"));
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            JsonNode headerNode = node.path("headers");
            if (headerNode.isObject()) {
                headerNode.fields().forEachRemaining(entry ->
                        headers.put(entry.getKey(), entry.getValue().asText()));
            }
            String body = objectMapper.writeValueAsString(Map.of(
                    "target", target == null ? "" : target,
                    "title", title == null ? "" : title,
                    "content", content == null ? "" : content));
            int status = restClient.method(method)
                    .uri(URI.create(url))
                    .headers(httpHeaders -> httpHeaders.setAll(headers))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .value();
            return status >= 200 && status < 300 ? null : "Webhook 返回状态: " + status;
        } catch (Exception e) {
            // 批8d：异常消息可能内嵌完整请求 URL（含 query 中的 token 等凭证），落库/日志前统一打码
            String error = LogMaskUtils.sanitize(e.getMessage());
            log.warn("Webhook 发送失败: err={}", error);
            return error;
        }
    }

    /** 仅允许携带请求体的方法；其余（如 GET）回落为 POST 以免出现 GET 带 JSON body 的歧义。 */
    private HttpMethod parseMethod(String raw) {
        try {
            HttpMethod method = HttpMethod.valueOf(raw.toUpperCase(Locale.ROOT));
            if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                return method;
            }
            return HttpMethod.POST;
        } catch (IllegalArgumentException e) {
            return HttpMethod.POST;
        }
    }
}
