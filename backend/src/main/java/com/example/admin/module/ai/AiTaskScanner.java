package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.ai.entity.AiTask;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.common.TenantContext;
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

    @Scheduled(fixedDelayString = "${app.ai.scan-interval-ms:30000}")
    public void scanTimeoutTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_TOLERANCE_MINUTES);
        List<Long> tenantIds = taskMapper.selectTenantIds();
        for (Long tenantId : tenantIds) {
            TenantContext.setTenantId(tenantId);
            try {
                List<AiTask> tasks = taskMapper.selectTimeoutTasks(tenantId, threshold);
                for (AiTask task : tasks) {
                    task.setStatus(AiTaskStatus.FAILED.name());
                    task.setErrorMsg(TIMEOUT_ERROR_MESSAGE);
                    task.setUpdatedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    log.warn("AI task {} timed out", task.getTaskNo());
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}

