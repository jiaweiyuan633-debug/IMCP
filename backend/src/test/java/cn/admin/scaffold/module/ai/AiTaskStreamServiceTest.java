package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.ai.vo.AiTaskVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 任务 SSE 轮询任务的生命周期与连接治理。SseEmitter 在未关联 HTTP 响应的
 * 纯单元测试环境下不触发 onCompletion/onError 回调，故无法直接断言「emitter 完成 → future 取消」；
 * 测试改为锁定可观测契约：
 * 1) 轮询任务首轮显式推延一个周期（线程池实现 (Runnable, Duration) 重载硬编码 delay=0）；
 * 2) emit() 对终态/异常调用 complete/completeWithError（emitter 完成后 send 抛 ISE）；
 * 3) cancelScheduledTask 的取消语义（cancel(false)、空引用安全）；
 * 4) 连接时先经 AiTaskService.openStream 校验访问权，拒绝则不建立连接、不调度轮询；
 * 5) 每用户连接上限，超限回收最旧连接（complete() 使旧 emitter send 抛 ISE）。
 */
class AiTaskStreamServiceTest {

    private static final long USER_ID = 1001L;

    private final AiTaskService taskService = mock(AiTaskService.class);
    private final ThreadPoolTaskScheduler scheduler = mock(ThreadPoolTaskScheduler.class);

    private AiTaskStreamService service() {
        return new AiTaskStreamService(taskService, scheduler);
    }

    /** 捕获 stream() 调度出的轮询任务（mock 调度器不会真正执行）。 */
    private Runnable capturePollTask() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleAtFixedRate(taskCaptor.capture(), any(Instant.class), any(Duration.class));
        return taskCaptor.getValue();
    }

    private static AiTaskVo taskVo(String status) {
        return AiTaskVo.builder().status(status).build();
    }

    private static AiTaskService.TaskStreamContext context() {
        return new AiTaskService.TaskStreamContext(1L, 1L);
    }

    @Test
    void schedulesPollingWithInitialDelayAndPeriodBothOfTwoSeconds() {
        when(taskService.openStream(1L, USER_ID)).thenReturn(context());
        service().stream(1L, USER_ID);

        ArgumentCaptor<Instant> startTime = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> period = ArgumentCaptor.forClass(Duration.class);
        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), startTime.capture(), period.capture());

        assertThat(period.getValue()).isEqualTo(Duration.ofSeconds(2));
        // 首轮推延一个周期：startTime 约等于「现在 + 2s」（容忍 1s 调度/断言间隙）
        Duration actualDelay = Duration.between(Instant.now(), startTime.getValue());
        assertThat(actualDelay).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(3));
    }

    @Test
    void accessDeniedAtConnectTimeRejectsWithoutSchedulingPolling() {
        // openStream 抛 FORBIDDEN（非管理员查看他人任务）→ 连接被拒，不建立 emitter 也不调度
        when(taskService.openStream(1L, USER_ID))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN));

        assertThatThrownBy(() -> service().stream(1L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(scheduler, never()).scheduleAtFixedRate(any(), any(), any());
    }

    @Test
    void terminalStatusCompletesEmitterSoPollingStops() {
        AiTaskStreamService service = service();
        when(taskService.openStream(1L, USER_ID)).thenReturn(context());
        SseEmitter emitter = service.stream(1L, USER_ID);
        Runnable pollTask = capturePollTask();

        when(taskService.detailForStream(1L)).thenReturn(taskVo(AiTaskStatus.SUCCEEDED.name()));
        pollTask.run();

        // 终态 → emit() 调用 complete()，emitter 已关闭：后续 send 抛 IllegalStateException
        assertThatThrownBy(() -> emitter.send("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detailFailureCompletesEmitterWithError() {
        AiTaskStreamService service = service();
        when(taskService.openStream(1L, USER_ID)).thenReturn(context());
        SseEmitter emitter = service.stream(1L, USER_ID);
        Runnable pollTask = capturePollTask();

        when(taskService.detailForStream(1L)).thenThrow(new RuntimeException("db down"));
        pollTask.run();

        // 查询失败 → emit() 调用 completeWithError()，emitter 同样已关闭
        assertThatThrownBy(() -> emitter.send("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonTerminalStatusKeepsEmitterOpen() {
        AiTaskStreamService service = service();
        when(taskService.openStream(1L, USER_ID)).thenReturn(context());
        SseEmitter emitter = service.stream(1L, USER_ID);
        Runnable pollTask = capturePollTask();

        when(taskService.detailForStream(1L)).thenReturn(taskVo(AiTaskStatus.RUNNING.name()));
        pollTask.run();

        // 非终态 → 未 complete，emitter 仍可继续 send（handler 未初始化时缓冲到 earlySendAttempts）
        assertThatCode(() -> emitter.send("x")).doesNotThrowAnyException();
    }

    @Test
    void connectEvictsOldestConnectionWhenPerUserLimitExceeded() {
        AiTaskStreamService service = service();
        service.setConnectionLimit(2);
        when(taskService.openStream(1L, USER_ID)).thenReturn(context());

        SseEmitter first = service.stream(1L, USER_ID);
        service.stream(1L, USER_ID);
        SseEmitter third = service.stream(1L, USER_ID);

        // 超限 → 回收最旧连接（列表头）并 complete()：旧 emitter send 抛 ISE，新连接不受影响
        assertThatThrownBy(() -> first.send("x")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> third.send("x")).doesNotThrowAnyException();
    }

    @Test
    void connectionLimitZeroMeansUnlimited() {
        AiTaskStreamService service = service();
        service.setConnectionLimit(0);
        when(taskService.openStream(1L, USER_ID)).thenReturn(context());

        SseEmitter first = service.stream(1L, USER_ID);
        service.stream(1L, USER_ID);
        service.stream(1L, USER_ID);

        assertThatCode(() -> first.send("x")).doesNotThrowAnyException();
    }

    @Test
    void cancelScheduledTaskCancelsFutureWithoutInterrupting() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<ScheduledFuture<?>> ref = new AtomicReference<>(future);

        AiTaskStreamService.cancelScheduledTask(ref);

        verify(future).cancel(false);
    }

    @Test
    void cancelScheduledTaskIsNullSafe() {
        // futureRef 未填充（调度尚未启动）时调用不抛异常
        assertThatCode(() -> AiTaskStreamService.cancelScheduledTask(new AtomicReference<>()))
                .doesNotThrowAnyException();
    }
}
