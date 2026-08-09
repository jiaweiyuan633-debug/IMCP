package com.example.admin.module.ai;

import com.example.admin.module.ai.vo.AiTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskStreamService {

    private static final Set<AiTaskStatus> TERMINAL_STATUS = EnumSet.of(
            AiTaskStatus.SUCCEEDED,
            AiTaskStatus.FAILED,
            AiTaskStatus.CANCELLED);

    private final AiTaskService taskService;
    private final ThreadPoolTaskScheduler scheduler;

    public SseEmitter stream(Long taskId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> emit(emitter, taskId), java.time.Duration.ofSeconds(2));
        emitter.onCompletion(() -> future.cancel(false));
        emitter.onTimeout(() -> future.cancel(false));
        emitter.onError(error -> future.cancel(false));
        return emitter;
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
