package com.example.admin.module.ai;

import com.example.admin.common.BusinessException;
import com.example.admin.common.RequestIdHolder;
import com.example.admin.common.ResultCode;
import com.example.admin.module.ai.entity.AiServiceConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAI 兼容大模型直连实现（{@link LlmProvider} SPI 默认实现）：
 * POST {baseUrl}/v1/chat/completions。
 * 独立 RestTemplate（120s 读超时），不占用共享 5s 超时的任务派发实例。
 *
 * <p>支持 provider 标识：openai（OpenAI 官方）与 local（Ollama / vLLM / LM Studio 等
 * 本地 OpenAI 兼容服务），二者协议一致，共用同一实现。
 */
@Component
@RequiredArgsConstructor
public class LlmChatClient implements LlmProvider {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    private final RestTemplate chatRestTemplate = buildRestTemplate();

    @Override
    public Set<String> providerNames() {
        return Set.of("openai", "local");
    }

    @Override
    public String chat(AiServiceConfigDO config, String model, List<Map<String, String>> messages, Double temperature) {
        String resolvedModel = StringUtils.hasText(model) ? model : config.getModel();
        if (!StringUtils.hasText(resolvedModel)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "模型服务未配置 model，请先在 AI 服务配置中填写模型名称");
        }
        Map<String, Object> body = new HashMap<>(8);
        body.put("model", resolvedModel);
        body.put("messages", messages == null ? List.of() : messages);
        body.put("stream", false);
        if (temperature != null) {
            body.put("temperature", temperature);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey());
        }
        if (RequestIdHolder.get() != null) {
            headers.set("X-Request-Id", RequestIdHolder.get());
        }
        try {
            ResponseEntity<Map> response = chatRestTemplate.postForEntity(
                    config.getBaseUrl() + "/v1/chat/completions",
                    new HttpEntity<>(body, headers),
                    Map.class);
            Map<?, ?> respBody = response.getBody();
            if (respBody == null) {
                throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE);
            }
            List<?> choices = (List<?>) respBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE);
            }
            Map<?, ?> first = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = first == null ? null : (Map<?, ?>) first.get("message");
            Object content = message == null ? null : message.get("content");
            return content == null ? "" : content.toString();
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
