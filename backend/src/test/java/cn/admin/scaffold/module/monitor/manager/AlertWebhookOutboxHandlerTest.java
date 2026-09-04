package cn.admin.scaffold.module.monitor.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** R4-1.13：投递时 SSRF 复核——非法地址按投递成功丢弃，绝不发起对外请求。 */
class AlertWebhookOutboxHandlerTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AlertWebhookOutboxHandler handler =
            new AlertWebhookOutboxHandler(restTemplate, new ObjectMapper());

    private String payload(String webhookUrl) {
        return "{\"ruleName\":\"CPU 告警\",\"metric\":\"cpu_usage\",\"severity\":\"WARNING\","
                + "\"currentValue\":88.5,\"threshold\":80,\"webhookUrl\":\"" + webhookUrl + "\"}";
    }

    @Test
    void dropsInternalIpUrlWithoutSending() {
        assertTrue(handler.handle(payload("http://169.254.169.254/latest/meta-data/")));
        verify(restTemplate, never()).postForEntity(any(), any(), any());
    }

    @Test
    void dropsLocalhostWithoutSending() {
        assertTrue(handler.handle(payload("http://localhost/hook")));
        verify(restTemplate, never()).postForEntity(any(), any(), any());
    }

    @Test
    void dropsNonHttpSchemeWithoutSending() {
        assertTrue(handler.handle(payload("file:///etc/passwd")));
        verify(restTemplate, never()).postForEntity(any(), any(), any());
    }

    @Test
    void sendsPublicLiteralUrl() {
        // 8.8.8.8 为公网 IP 字面量，本地解析通过校验并真实发起投递
        assertTrue(handler.handle(payload("http://8.8.8.8/hook")));
        verify(restTemplate).postForEntity(eq("http://8.8.8.8/hook"), any(), eq(String.class));
    }

    @Test
    void treatsBlankUrlAsDeliveredWithoutSending() {
        assertTrue(handler.handle(payload("")));
        verify(restTemplate, never()).postForEntity(any(), any(), any());
    }
}
