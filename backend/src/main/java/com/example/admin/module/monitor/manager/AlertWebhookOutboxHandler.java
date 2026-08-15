package com.example.admin.module.monitor.manager;

import com.example.admin.common.LogMaskUtils;
import com.example.admin.common.SsrfUrlValidator;
import com.example.admin.common.outbox.OutboxHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警 Webhook 发件箱处理器：由 {@link OutboxDispatcher} 可靠投递（重试+退避+熔断）。
 *
 * <p>原来 {@link AlertWebhookManager#send} 直接发 Webhook，失败仅 log.warn 静默丢弃；
 * 现改为事务内写发件箱、提交后经本处理器投递——网络抖动由 spring-retry 重试，
 * 持续失败由 Resilience4j 熔断短路，彻底失败由发件箱指数退避回投，不再静默丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertWebhookOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "alert-webhook";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    @CircuitBreaker(name = "alertWebhook")
    public boolean handle(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String webhookUrl = node.path("webhookUrl").asText();
            if (webhookUrl.isBlank()) {
                return true; // 无 Webhook 地址视为投递成功，避免无限重试
            }
            // R4-1.13：投递时复核 SSRF。发件箱可能残留修复前入库的地址，且主机名解析结果可能变化，
            // 保存时静态校验无法覆盖"主机名指向内网 IP"；永久非法地址按投递成功丢弃，避免重试/退避打空转。
            String error = SsrfUrlValidator.validateOutboundHttpUrlWithDns(webhookUrl);
            if (error != null) {
                log.error("告警 Webhook 地址被 SSRF 校验拒绝，丢弃该投递（rule={}）：{}",
                        node.path("ruleName").asText(), error);
                return true;
            }
            Map<String, Object> body = new HashMap<>(8);
            body.put("ruleName", node.path("ruleName").asText());
            body.put("metric", node.path("metric").asText());
            body.put("severity", node.path("severity").asText());
            body.put("currentValue", node.path("currentValue").asDouble());
            body.put("threshold", node.path("threshold").asDouble());
            restTemplate.postForEntity(webhookUrl, body, String.class);
            return true;
        } catch (RestClientException exception) {
            // 批8d：异常消息可能内嵌请求 URL（含 webhook 地址查询凭证），日志前统一打码
            log.warn("Alert webhook send failed: {}", LogMaskUtils.sanitize(exception.getMessage()));
            throw exception; // 由 @Retryable 重试
        } catch (Exception exception) {
            // JSON 解析等本地错误重试无意义，记日志并按失败处理（发件箱退避后仍会重试，留人工干预）
            log.error("Alert webhook payload invalid: {}", LogMaskUtils.sanitize(payload), exception);
            return false;
        }
    }
}
