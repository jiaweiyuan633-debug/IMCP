package com.example.admin.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 业务指标采集。
 *
 * <p>将核心业务事件（登录、定时任务执行、操作日志/字段审计写入）以计数器暴露到
 * {@code /actuator/prometheus}，供 Grafana 面板展示与告警规则使用。
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {

    private final MeterRegistry meterRegistry;

    public void loginSuccess() {
        increment("admin.login.total", "result", "success");
    }

    public void loginFailure() {
        increment("admin.login.total", "result", "failure");
    }

    public void jobExecution(boolean success) {
        increment("admin.job.execution.total", "result", success ? "success" : "failure");
    }

    public void operLogWritten() {
        increment("admin.operlog.written.total");
    }

    public void fieldAuditWritten() {
        increment("admin.fieldaudit.written.total");
    }

    private void increment(String name, String... tags) {
        Counter.builder(name)
                .description("Business event counter: " + name)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }
}
