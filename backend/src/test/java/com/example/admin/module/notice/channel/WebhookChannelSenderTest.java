package com.example.admin.module.notice.channel;

import com.example.admin.module.notice.ChannelType;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

/**
 * 通用 Webhook 渠道发送器：SSRF 防护（拒绝内网目标）+ 请求构造验证。
 *
 * <p>原测试起本地 127.0.0.1 HttpServer 充当 webhook 目标，R4-1.13 加入 SSRF 防护后
 * 该地址被正确拒绝，故改为 mock RestClient 验证请求构造（离线可跑、无端口依赖）。
 */
class WebhookChannelSenderTest {

    /** 公网 IP 字面量：本地解析即可通过 SSRF 校验，不依赖 DNS。 */
    private static final String PUBLIC_URL = "http://8.8.8.8/hook";

    /** RETURNS_SELF：uri/headers/body 链式调用自动返回自身，仅 retrieve() 需显式 stub。 */
    private final RestClient.RequestBodyUriSpec uriSpec =
            mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
    private final RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    private final RestClient restClient = mock(RestClient.class);

    private WebhookChannelSender senderReturning(int status) {
        when(restClient.method(HttpMethod.POST)).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.status(status).build());
        return new WebhookChannelSender(new ObjectMapper(), restClient);
    }

    private SysChannelConfigDO config(String json) {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setChannelType("WEBHOOK");
        config.setConfigJson(json);
        return config;
    }

    @Test
    void postsJsonWithCustomHeadersAndReturnsNullOn2xx() {
        WebhookChannelSender sender = senderReturning(200);
        String json = "{\"url\":\"" + PUBLIC_URL + "\",\"headers\":{\"Authorization\":\"Bearer test-token\"}}";

        String error = sender.send(config(json), "g=123", "告警", "磁盘占用 92%");

        assertThat(error).isNull();
        verify(restClient).method(HttpMethod.POST);
        verify(uriSpec).uri(URI.create(PUBLIC_URL));

        // 请求体 JSON
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(uriSpec).body(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .contains("\"target\":\"g=123\"")
                .contains("\"title\":\"告警\"")
                .contains("\"content\":\"磁盘占用 92%\"");

        // 自定义头 + Content-Type
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<HttpHeaders>> headersConsumer = ArgumentCaptor.forClass(Consumer.class);
        verify(uriSpec).headers(headersConsumer.capture());
        HttpHeaders headers = new HttpHeaders();
        headersConsumer.getValue().accept(headers);
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer test-token");
        assertThat(headers.getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void returnsErrorOnNon2xx() {
        WebhookChannelSender sender = senderReturning(500);
        String json = "{\"url\":\"" + PUBLIC_URL + "\"}";

        String error = sender.send(config(json), "t", "标题", "内容");

        assertThat(error).isNotNull().contains("500");
    }

    @Test
    void rejectsInternalUrlWithoutSending() {
        WebhookChannelSender sender = new WebhookChannelSender(new ObjectMapper(), restClient);
        String json = "{\"url\":\"http://127.0.0.1:9000/hook\"}";

        String error = sender.send(config(json), "t", "标题", "内容");

        assertThat(error).isNotNull().contains("不合法");
        verify(restClient, never()).method(argThat(method -> true));
    }

    @Test
    void returnsErrorWhenUrlMissing() {
        WebhookChannelSender sender = new WebhookChannelSender(new ObjectMapper(), restClient);

        String error = sender.send(config("{\"headers\":{}}"), "t", "标题", "内容");

        assertThat(error).isNotNull().contains("URL");
    }

    @Test
    void supportsWebhookType() {
        assertThat(new WebhookChannelSender(new ObjectMapper()).supports()).isEqualTo(ChannelType.WEBHOOK);
    }
}
