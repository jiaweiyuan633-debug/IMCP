package com.example.admin.module.ai;

import com.example.admin.module.ai.vo.AiTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskStreamService {

    private static final long SSE_TIMEOUT_MILLIS = 180_000L;
    private static final long POLL_INTERVAL_SECONDS = 2;
    private static final Set<AiTaskStatus> TERMINAL_STATUS = EnumSet.of(
            AiTaskStatus.SUCCEEDED,
            AiTaskStatus.FAILED,
            AiTaskStatus.CANCELLED);

    private final AiTaskService taskService;
    private final ThreadPoolTaskScheduler scheduler;

    /**
     * R4-1.4：先注册生命周期回调，再调度轮询任务，且首轮显式推延一个周期。
     * 原实现 scheduleAtFixedRate 默认 initialDelay=0，首个 emit 可能抢在回调注册与
     * HTTP 响应初始化之前执行：若首轮即达终态 complete()，async 完成回调先触发而
     * onCompletion 委托尚未设置，ScheduledFuture 永不被取消，定时任务每 2s 空转查询
     * 一次任务详情直至 180s 超时，构成定时任务/调度线程泄漏面。
     * 回调先注册 + 首轮推延后，完成路径触发时委托与 futureRef 必然已就绪，总能取消。
     */
    public SseEmitter stream(Long taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable stopPolling = () -> cancelScheduledTask(futureRef);
        emitter.onCompletion(stopPolling);
        emitter.onTimeout(stopPolling);
        emitter.onError(error -> cancelScheduledTask(futureRef));
        // ThreadPoolTaskScheduler 覆写了接口默认实现，其 (Runnable, Duration) 重载
        // 硬编码 initialDelay=0（首轮立即执行），必须用 Instant 重载显式推延一个周期
        futureRef.set(scheduler.scheduleAtFixedRate(
                () -> emit(emitter, taskId),
                Instant.now().plus(Duration.ofSeconds(POLL_INTERVAL_SECONDS)),
                Duration.ofSeconds(POLL_INTERVAL_SECONDS)));
        return emitter;
    }

    /** 取消轮询任务（可重入幂等；不中断正在执行的轮询）。包内可见供测试。 */
    static void cancelScheduledTask(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.get();
        if (future != null) {
            future.cancel(false);
        }
    }

    private void emit(SseEmitter emitter, Long taskId) {
        try {
            AiTaskVo task = taskService.detail(taskId);
            emitter.send(SseEmitter.event().name("task").data(task));
            if (isTerminal(task.getStatus())) {
                emitter.complete();
            }
        } catch (IOException | RuntimeException exception) {
            try {
                emitter.completeWithError(exception);
            } catch (IllegalStateException ignored) {
                // emitter already closed
            }
        }
    }

    private boolean isTerminal(String status) {
        try {
            return TERMINAL_STATUS.contains(AiTaskStatus.valueOf(status));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
