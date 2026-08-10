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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiPythonClient {

    private final RestTemplate restTemplate;

    public Map<String, Object> createTask(
            AiServiceConfigDO config,
            String taskNo,
            String bizType,
            Map<String, Object> params,
            String callbackUrl) {
        Map<String, Object> body = new HashMap<>(8);
        body.put("task_no", taskNo);
        body.put("biz_type", bizType);
        body.put("params", params == null ? Map.of() : params);
        body.put("callback_url", callbackUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 任务接口鉴权：AI 服务校验 Authorization: Bearer（与回调 HMAC 共用 ApiServiceConfig.apiKey）
        if (StringUtils.hasText(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey());
        }
        if (RequestIdHolder.get() != null) {
            headers.set("X-Request-Id", RequestIdHolder.get());
        }
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    config.getBaseUrl() + "/api/v1/tasks",
                    new HttpEntity<>(body, headers),
                    Map.class);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}

