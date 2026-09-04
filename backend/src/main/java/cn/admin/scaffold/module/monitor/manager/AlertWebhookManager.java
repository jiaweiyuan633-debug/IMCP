package cn.admin.scaffold.module.monitor.manager;

import cn.admin.scaffold.common.outbox.OutboxPublisher;
import cn.admin.scaffold.module.monitor.entity.SysAlertRuleDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 告警 Webhook 门面：把告警事件写入事务发件箱，由 {@link AlertWebhookOutboxHandler} 可靠投递。
 *
 * <p>可靠性语义：send 在业务事务内写 sys_outbox，事务提交后发件箱才外发——
 * 业务状态与 Webhook 通知达到"至少一次"一致，网络故障不静默丢失，由发件箱指数退避兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertWebhookManager {

    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void send(SysAlertRuleDO rule, String severity, double value) {
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
            payload.put("webhookUrl", rule.getWebhookUrl());
            outboxPublisher.publish(AlertWebhookOutboxHandler.TOPIC, objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            // 序列化/写发件箱异常属本地故障，记日志降级（不阻断告警主流程）
            log.error("写入告警 Webhook 发件箱失败，rule={}", rule.getRuleName(), exception);
        }
    }
}
