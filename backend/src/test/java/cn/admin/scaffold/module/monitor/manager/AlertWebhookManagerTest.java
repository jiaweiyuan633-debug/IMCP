package cn.admin.scaffold.module.monitor.manager;

import cn.admin.scaffold.common.outbox.OutboxPublisher;
import cn.admin.scaffold.module.monitor.entity.SysAlertRuleDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertWebhookManagerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AlertWebhookManager alertWebhookManager;

    @Test
    void publishesOutboxWhenWebhookConfigured() {
        SysAlertRuleDO rule = new SysAlertRuleDO();
        rule.setRuleName("CPU 告警");
        rule.setWebhookUrl("https://example.com/hook");

        alertWebhookManager.send(rule, "WARNING", 88.5);

        verify(outboxPublisher).publish(eq(AlertWebhookOutboxHandler.TOPIC), anyString());
    }

    @Test
    void skipsOutboxWhenWebhookNotConfigured() {
        SysAlertRuleDO rule = new SysAlertRuleDO();
        rule.setRuleName("内存告警");

        alertWebhookManager.send(rule, "WARNING", 90.0);

        verify(outboxPublisher, never()).publish(anyString(), any());
    }

    @Test
    void payloadIsValidJson() throws Exception {
        SysAlertRuleDO rule = new SysAlertRuleDO();
        rule.setRuleName("CPU 告警");
        rule.setWebhookUrl("https://example.com/hook");

        alertWebhookManager.send(rule, "WARNING", 88.5);

        org.mockito.ArgumentCaptor<String> captor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(outboxPublisher).publish(eq(AlertWebhookOutboxHandler.TOPIC), captor.capture());
        objectMapper.readTree(captor.getValue());
    }
}
