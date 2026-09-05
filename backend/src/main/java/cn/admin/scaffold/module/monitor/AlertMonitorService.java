package cn.admin.scaffold.module.monitor;

import cn.admin.scaffold.common.ScheduledTaskLock;
import cn.admin.scaffold.module.monitor.entity.SysAlertRuleDO;
import cn.admin.scaffold.module.monitor.manager.AlertWebhookManager;
import cn.admin.scaffold.module.monitor.mapper.SysAlertRuleMapper;
import cn.admin.scaffold.module.monitor.vo.ServerMonitorVo;
import cn.admin.scaffold.module.system.SystemNoticeService;
import cn.admin.scaffold.module.system.entity.SysNoticeDO;
import cn.admin.scaffold.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

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
    private final StringRedisTemplate redisTemplate;
    private final AlertWebhookManager alertWebhookManager;
    private final ScheduledTaskLock scheduledTaskLock;

    @Scheduled(
            initialDelayString = "${app.alert-check-initial-delay-ms:15000}",
            fixedDelayString = "${app.alert-check-interval-ms:60000}")
    public void scheduledCheck() {
        // 多副本部署下仅一个实例执行，防止重复产生告警通知
        if (!scheduledTaskLock.tryLock("alert-monitor-check", Duration.ofSeconds(55))) {
            return;
        }
        try {
            checkNow();
        } catch (RuntimeException exception) {
            log.error("Alert monitor check failed", exception);
        } finally {
            scheduledTaskLock.unlock("alert-monitor-check");
        }
    }

    public int checkNow() {
        ServerMonitorVo monitor = serverMonitorService.get();
        List<SysAlertRuleDO> rules = ruleMapper.selectAllEnabledIgnoreTenant();
        int triggered = 0;
        for (SysAlertRuleDO rule : rules) {
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

    private void sendNotice(SysAlertRuleDO rule, double value) {
        String severity = rule.getSeverity() == null ? DEFAULT_SEVERITY : rule.getSeverity();
        SysNoticeDO notice = new SysNoticeDO();
        notice.setNoticeTitle("[" + severity + "] " + rule.getRuleName());
        notice.setNoticeType(NOTICE_TYPE);
        notice.setNoticeContent(String.format(
                "级别 %s，监控指标 %s 当前值 %.2f，已触发阈值 %.2f。",
                severity, rule.getMetric(), value, rule.getThreshold()));
        notice.setStatus(NOTICE_STATUS);
        noticeService.create(notice);
        // create 内部已按发布线程租户（= rule.getTenantId()）广播公告，此处不再重复推送
        alertWebhookManager.send(rule, severity, value);
        log.warn("Alert triggered: {} severity={} current={} threshold={}", rule.getRuleName(), severity, value, rule.getThreshold());
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
