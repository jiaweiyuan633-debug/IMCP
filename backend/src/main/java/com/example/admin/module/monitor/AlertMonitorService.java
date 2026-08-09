package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.monitor.entity.SysAlertRule;
import com.example.admin.module.monitor.mapper.SysAlertRuleMapper;
import com.example.admin.module.monitor.vo.ServerMonitorVo;
import com.example.admin.module.system.NoticeSseService;
import com.example.admin.module.system.SystemNoticeService;
import com.example.admin.module.system.entity.SysNotice;
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

    private final SysAlertRuleMapper ruleMapper;
    private final ServerMonitorService serverMonitorService;
    private final SystemNoticeService noticeService;
    private final NoticeSseService noticeSseService;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(
            initialDelayString = "${app.alert-check-initial-delay-ms:15000}",
            fixedDelayString = "${app.alert-check-interval-ms:60000}")
    public void scheduledCheck() {
        try {
            checkNow();
        } catch (Exception exception) {
            log.error("Alert monitor check failed", exception);
        }
    }

    public int checkNow() {
        ServerMonitorVo monitor = serverMonitorService.get();
        List<SysAlertRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<SysAlertRule>()
                .eq(SysAlertRule::getEnabled, 1));
        int triggered = 0;
        for (SysAlertRule rule : rules) {
            double value = readMetric(monitor, rule.getMetric());
            if (!isTriggered(value, rule.getOperator(), rule.getThreshold().doubleValue())) {
                continue;
            }
            String redisKey = ALERT_KEY_PREFIX + rule.getId();
            Boolean first = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(first)) {
                continue;
            }
            sendNotice(rule, value);
            triggered++;
        }
        return triggered;
    }

    private void sendNotice(SysAlertRule rule, double value) {
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle("告警：" + rule.getRuleName());
        notice.setNoticeType(1);
        notice.setNoticeContent(String.format(
                "监控指标 %s 当前值 %.2f，已触发阈值 %.2f。",
                rule.getMetric(), value, rule.getThreshold()));
        notice.setStatus(1);
        noticeService.create(notice);
        noticeSseService.publishAll(notice);
        log.warn("Alert triggered: {} current={} threshold={}", rule.getRuleName(), value, rule.getThreshold());
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
