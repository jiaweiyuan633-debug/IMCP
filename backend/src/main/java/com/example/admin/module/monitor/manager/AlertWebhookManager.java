package com.example.admin.module.monitor.manager;

import com.example.admin.module.monitor.entity.SysAlertRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertWebhookManager {

    private final RestTemplate restTemplate;

    public void send(SysAlertRule rule, String severity, double value) {
        if (rule.getWebhookUrl() == null || rule.getWebhookUrl().isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>(8);
            payload.put("ruleName", rule.getRuleName());
            payload.put("metric", rule.getMetric());
            payload.put("severity", severity);
            payload.put("currentValue", value);
            payload.put("threshold", rule.getThreshold());
            restTemplate.postForEntity(rule.getWebhookUrl(), payload, String.class);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Alert webhook failed for {}", rule.getRuleName(), exception);
        }
    }
}
