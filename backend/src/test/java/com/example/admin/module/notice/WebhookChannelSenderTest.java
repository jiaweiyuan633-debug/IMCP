package com.example.admin.module.notice;

import com.example.admin.module.notice.channel.WebhookChannelSender;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用 Webhook 渠道发送器：基于临时 HttpServer 验证请求体、自定义头与状态码判定。
 */
class WebhookChannelSenderTest {

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] resp = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    private SysChannelConfigDO config(String json) {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setChannelType("WEBHOOK");
        config.setConfigJson(json);
        return config;
    }

    @Test
    void postsJsonWithCustomHeadersAndReturnsNullOn2xx() {
        WebhookChannelSender sender = new WebhookChannelSender(new ObjectMapper());
        String json = String.format("{\"url\":\"%s\",\"headers\":{\"Authorization\":\"Bearer test-token\"}}", baseUrl());

        String error = sender.send(config(json), "g=123", "告警", "磁盘占用 92%");

        assertThat(error).isNull();
        assertThat(receivedAuth.get()).isEqualTo("Bearer test-token");
        assertThat(receivedBody.get()).contains("\"target\":\"g=123\"");
        assertThat(receivedBody.get()).contains("\"title\":\"告警\"");
        assertThat(receivedBody.get()).contains("\"content\":\"磁盘占用 92%\"");
    }

    @Test
    void returnsErrorOnNon2xx() {
        responseStatus.set(500);
        WebhookChannelSender sender = new WebhookChannelSender(new ObjectMapper());
        String json = String.format("{\"url\":\"%s\"}", baseUrl());

        String error = sender.send(config(json), "t", "标题", "内容");

        assertThat(error).isNotNull().contains("500");
    }

    @Test
    void returnsErrorWhenUrlMissing() {
        WebhookChannelSender sender = new WebhookChannelSender(new ObjectMapper());

        String error = sender.send(config("{\"headers\":{}}"), "t", "标题", "内容");

        assertThat(error).isNotNull().contains("URL");
    }

    @Test
    void supportsWebhookType() {
        assertThat(new WebhookChannelSender(new ObjectMapper()).supports()).isEqualTo(ChannelType.WEBHOOK);
    }
}
