package com.example.admin.module.monitor.manager;

import com.example.admin.module.monitor.entity.SysAlertRuleDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AlertWebhookManagerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AlertWebhookManager alertWebhookManager;

    @Test
    void sendsPayloadWhenWebhookConfigured() {
        SysAlertRuleDO rule = new SysAlertRuleDO();
        rule.setRuleName("CPU 告警");
        rule.setWebhookUrl("https://example.com/hook");

        alertWebhookManager.send(rule, "WARNING", 88.5);

        verify(restTemplate).postForEntity(eq("https://example.com/hook"), any(), eq(String.class));
    }

    @Test
    void skipsWebhookWhenNotConfigured() {
        SysAlertRuleDO rule = new SysAlertRuleDO();
        rule.setRuleName("内存告警");

        alertWebhookManager.send(rule, "WARNING", 90.0);

        verifyNoInteractions(restTemplate);
    }
}
