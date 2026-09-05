package cn.admin.scaffold.module.ai;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.admin.scaffold.common.MessageBizType;
import cn.admin.scaffold.common.ScheduledTaskLock;
import cn.admin.scaffold.module.ai.entity.AiTaskDO;
import cn.admin.scaffold.module.ai.mapper.AiTaskMapper;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskScanner {

    private static final int TIMEOUT_TOLERANCE_MINUTES = 1;
    private static final String TIMEOUT_ERROR_MESSAGE = "任务执行超时";

    private final AiTaskMapper taskMapper;
    private final SystemMessageService messageService;
    private final ScheduledTaskLock scheduledTaskLock;

    @Scheduled(fixedDelayString = "${app.ai.scan-interval-ms:30000}")
    public void scanTimeoutTasks() {
        // 多副本部署下仅一个实例执行，防止重复扫描与重复通知
        if (!scheduledTaskLock.tryLock("ai-task-scanner", Duration.ofSeconds(25))) {
            return;
        }
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_TOLERANCE_MINUTES);
            List<Long> tenantIds = taskMapper.selectTenantIds();
            for (Long tenantId : tenantIds) {
                TenantContext.setTenantId(tenantId);
                try {
                    List<AiTaskDO> tasks = taskMapper.selectTimeoutTasks(tenantId, threshold);
                    for (AiTaskDO task : tasks) {
                        // 条件更新：仅当任务仍处于非终态时才置为超时失败，避免覆盖并发回调已写入的终态，
                        // 并据此跳过重复通知（updated == 0 说明已被回调抢占处理）
                        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiTaskDO>()
                                .eq(AiTaskDO::getId, task.getId())
                                .in(AiTaskDO::getStatus, AiTaskStatus.PENDING.name(), AiTaskStatus.QUEUED.name(), AiTaskStatus.RUNNING.name())
                                .set(AiTaskDO::getStatus, AiTaskStatus.FAILED.name())
                                .set(AiTaskDO::getErrorMsg, TIMEOUT_ERROR_MESSAGE)
                                .set(AiTaskDO::getUpdatedAt, LocalDateTime.now()));
                        if (updated == 0) {
                            continue;
                        }
                        notifyTimeout(task);
                        log.warn("AI task {} timed out", task.getTaskNo());
                    }
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            scheduledTaskLock.unlock("ai-task-scanner");
        }
    }

    private void notifyTimeout(AiTaskDO task) {
        if (task.getCreatedBy() == null) {
            return;
        }
        messageService.sendSystemToUsers(List.of(task.getCreatedBy()), task.getTenantId(),
                "AI 任务失败",
                "AI 任务「" + task.getTaskNo() + "」执行失败：" + TIMEOUT_ERROR_MESSAGE,
                MessageBizType.AI, task.getId());
    }
}

