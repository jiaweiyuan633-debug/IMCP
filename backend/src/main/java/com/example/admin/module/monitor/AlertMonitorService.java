package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.monitor.entity.SysAlertRule;
import com.example.admin.module.monitor.mapper.SysAlertRuleMapper;
import com.example.admin.module.monitor.vo.ServerMonitorVo;
import com.example.admin.module.system.NoticeSseService;
import com.example.admin.module.system.SystemNoticeService;
import com.example.admin.module.system.entity.SysNotice;
import com.example.admin.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertMonitorService {

    private static final String ALERT_KEY_PREFIX = "alert:notify:";
    private static final String DEFAULT_SEVERITY = "WARNING";
    private static final int DEFAULT_SILENCE_MINUTES = 10;
    private static final int MIN_SILENCE_MINUTES = 1;
    private static final int NOTICE_TYPE = 1;
    private static final int NOTICE_STATUS = 1;

    private final SysAlertRuleMapper ruleMapper;
    private final ServerMonitorService serverMonitorService;
    private final SystemNoticeService noticeService;
    private final NoticeSseService noticeSseService;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    @Scheduled(
            initialDelayString = "${app.alert-check-initial-delay-ms:15000}",
            fixedDelayString = "${app.alert-check-interval-ms:60000}")
    public void scheduledCheck() {
        try {
            checkNow();
        } catch (RuntimeException exception) {
            log.error("Alert monitor check failed", exception);
        }
    }

    public int checkNow() {
        ServerMonitorVo monitor = serverMonitorService.get();
        List<SysAlertRule> rules = ruleMapper.selectAllEnabledIgnoreTenant();
        int triggered = 0;
        for (SysAlertRule rule : rules) {
            TenantContext.setTenantId(rule.getTenantId());
            try {
                double value = readMetric(monitor, rule.getMetric());
                if (!isTriggered(value, rule.getOperator(), rule.getThreshold().doubleValue())) {
                    continue;
                }
                String redisKey = ALERT_KEY_PREFIX + rule.getId();
                int silenceMinutes = rule.getSilenceMinutes() == null
                        ? DEFAULT_SILENCE_MINUTES
                        : Math.max(rule.getSilenceMinutes(), MIN_SILENCE_MINUTES);
                Boolean first = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(silenceMinutes));
                if (!Boolean.TRUE.equals(first)) {
                    continue;
                }
                sendNotice(rule, value);
                triggered++;
            } finally {
                TenantContext.clear();
            }
        }
        return triggered;
    }

    private void sendNotice(SysAlertRule rule, double value) {
        String severity = rule.getSeverity() == null ? DEFAULT_SEVERITY : rule.getSeverity();
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle("[" + severity + "] " + rule.getRuleName());
        notice.setNoticeType(NOTICE_TYPE);
        notice.setNoticeContent(String.format(
                "级别 %s，监控指标 %s 当前值 %.2f，已触发阈值 %.2f。",
                severity, rule.getMetric(), value, rule.getThreshold()));
        notice.setStatus(NOTICE_STATUS);
        noticeService.create(notice);
        noticeSseService.publishAll(notice);
        sendWebhook(rule, severity, value);
        log.warn("Alert triggered: {} severity={} current={} threshold={}", rule.getRuleName(), severity, value, rule.getThreshold());
    }

    private void sendWebhook(SysAlertRule rule, String severity, double value) {
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

    private double readMetric(ServerMonitorVo monitor, String metric) {
        if ("CPU_USAGE".equals(metric)) {
            return monitor.getCpuLoad();
        }
        if ("MEMORY_USAGE".equals(metric)) {
            return monitor.getMemUsagePercent();
        }
        if ("JVM_USAGE".equals(metric)) {
            return monitor.getJvmUsagePercent();
        }
        if ("DISK_USAGE".equals(metric)) {
            return monitor.getDisks().stream()
                    .map(ServerMonitorVo.DiskInfo::getUsagePercent)
                    .max(Double::compareTo)
                    .orElse(0.0);
        }
        return 0;
    }

    private boolean isTriggered(double value, String operator, double threshold) {
        return "lt".equals(operator) ? value < threshold : value > threshold;
    }
}
