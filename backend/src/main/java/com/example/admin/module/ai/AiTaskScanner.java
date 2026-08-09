package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.MessageBizType;
import com.example.admin.module.ai.entity.AiTaskDO;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(fixedDelayString = "${app.ai.scan-interval-ms:30000}")
    public void scanTimeoutTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_TOLERANCE_MINUTES);
        List<Long> tenantIds = taskMapper.selectTenantIds();
        for (Long tenantId : tenantIds) {
            TenantContext.setTenantId(tenantId);
            try {
                List<AiTaskDO> tasks = taskMapper.selectTimeoutTasks(tenantId, threshold);
                for (AiTaskDO task : tasks) {
                    task.setStatus(AiTaskStatus.FAILED.name());
                    task.setErrorMsg(TIMEOUT_ERROR_MESSAGE);
                    task.setUpdatedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    notifyTimeout(task);
                    log.warn("AI task {} timed out", task.getTaskNo());
                }
            } finally {
                TenantContext.clear();
            }
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

