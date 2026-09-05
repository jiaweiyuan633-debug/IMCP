package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.ai.vo.AiTaskVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AiTaskStreamService {

    private static final long SSE_TIMEOUT_MILLIS = 180_000L;
    private static final long POLL_INTERVAL_SECONDS = 2;
    private static final int DEFAULT_CONNECTION_LIMIT = 5;
    private static final Set<AiTaskStatus> TERMINAL_STATUS = EnumSet.of(
            AiTaskStatus.SUCCEEDED,
            AiTaskStatus.FAILED,
            AiTaskStatus.CANCELLED);

    private final AiTaskService taskService;
    private final ThreadPoolTaskScheduler scheduler;
    private int connectionLimit = DEFAULT_CONNECTION_LIMIT;

    /** 每用户活跃 SSE 连接；连接完成/超时/异常时移出。 */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public AiTaskStreamService(AiTaskService taskService, ThreadPoolTaskScheduler scheduler) {
        this.taskService = taskService;
        this.scheduler = scheduler;
    }

    /**
     * 每用户并发连接上限，超限回收最旧连接。默认 5，可用
     * app.ai-task-sse-max-connections-per-user 覆盖；配置 {@code <=0} 表示不限制。
     * 票据可无限签发，无上限时单账号可循环取票开流，每条连接占用一个异步 Servlet 请求
     * 外加共享调度池上一个 2s 轮询任务，构成资源耗尽面（与公告 SSE 同一防护模式）。
     */
    @Value("${app.ai-task-sse-max-connections-per-user:5}")
    public void setConnectionLimit(int connectionLimit) {
        this.connectionLimit = connectionLimit;
    }

    /**
     * 建立 AI 任务实时推送流。
     *
     * <p>根因修复：原实现在调度线程直接调用 detail()，其 checkDataScope 走
     * SecurityUtils.getLoginUser()，而轮询线程 SecurityContextHolder 为空 → 必然抛
     * UNAUTHORIZED 使流在 2s 内 completeWithError，AI 任务实时推送整体不可用；且轮询
     * 线程 TenantContext 恒默认 1，非租户 1 任务 selectById 直接落空。现改为：连接时
     * （请求线程）先经 {@link AiTaskService#openStream} 完成访问校验并捕获任务租户，
     * 轮询线程 emit 内恢复 TenantContext 后经 detailForStream 只读（不再触碰
     * SecurityContext）。校验失败在此抛异常，HTTP 层以 4xx 拒绝，不建立连接。
     *
     * @param userId 票据消费出的用户 id（EventSource 无法携带 Authorization 头，票据即身份）
     */
    public SseEmitter stream(Long taskId, Long userId) {
        AiTaskService.TaskStreamContext context = taskService.openStream(taskId, userId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        enforceConnectionLimit(userId);
        // 先注册生命周期回调，再调度轮询任务，且首轮显式推延一个周期。
        // 原实现 scheduleAtFixedRate 默认 initialDelay=0，首个 emit 可能抢在回调注册与
        // HTTP 响应初始化之前执行：若首轮即达终态 complete()，async 完成回调先触发而
        // onCompletion 委托尚未设置，ScheduledFuture 永不被取消，定时任务每 2s 空转查询
        // 一次任务详情直至 180s 超时，构成定时任务/调度线程泄漏面。
        // 回调先注册 + 首轮推延后，完成路径触发时委托与 futureRef 必然已就绪，总能取消。
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        Runnable stopPolling = () -> cancelScheduledTask(futureRef);
        emitter.onCompletion(stopPolling);
        emitter.onTimeout(stopPolling);
        emitter.onError(error -> cancelScheduledTask(futureRef));
        // ThreadPoolTaskScheduler 覆写了接口默认实现，其 (Runnable, Duration) 重载
        // 硬编码 initialDelay=0（首轮立即执行），必须用 Instant 重载显式推延一个周期
        futureRef.set(scheduler.scheduleAtFixedRate(
                () -> emit(emitter, context),
                Instant.now().plus(Duration.ofSeconds(POLL_INTERVAL_SECONDS)),
                Duration.ofSeconds(POLL_INTERVAL_SECONDS)));
        return emitter;
    }

    /** 回收该用户最旧连接直至不超过上限；被回收连接 complete() 释放容器异步线程。 */
    private void enforceConnectionLimit(Long userId) {
        if (connectionLimit <= 0) {
            return;
        }
        List<SseEmitter> list = emitters.get(userId);
        while (list != null && list.size() > connectionLimit) {
            SseEmitter oldest = list.get(0);
            remove(userId, oldest);
            try {
                oldest.complete();
            } catch (IllegalStateException ignored) {
                // 连接已被并发关闭；移除逻辑幂等，无需处理
            }
        }
    }

    /** 取消轮询任务（可重入幂等；不中断正在执行的轮询）。包内可见供测试。 */
    static void cancelScheduledTask(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> future = futureRef.get();
        if (future != null) {
            future.cancel(false);
        }
    }

    private void emit(SseEmitter emitter, AiTaskService.TaskStreamContext context) {
        // 调度线程无租户上下文（默认回落 1），按连接时捕获的任务租户恢复，否则
        // detailForStream 的 selectById 会被租户拦截器按租户 1 过滤落空。finally 清理
        // 防线程池复用污染后续任务。
        TenantContext.setTenantId(context.tenantId());
        try {
            AiTaskVo task = taskService.detailForStream(context.taskId());
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
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isTerminal(String status) {
        try {
            return TERMINAL_STATUS.contains(AiTaskStatus.valueOf(status));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
