package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.ai.entity.AiTask;
import com.example.admin.module.ai.mapper.AiTaskMapper;
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

    private final AiTaskMapper taskMapper;

    @Scheduled(fixedDelayString = "${app.ai.scan-interval-ms:30000}")
    public void scanTimeoutTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(1);
        List<AiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiTask>()
                .in(AiTask::getStatus,
                        AiTaskStatus.PENDING.name(),
                        AiTaskStatus.QUEUED.name(),
                        AiTaskStatus.RUNNING.name())
                .lt(AiTask::getUpdatedAt, threshold));
        for (AiTask task : tasks) {
            task.setStatus(AiTaskStatus.FAILED.name());
            task.setErrorMsg("任务执行超时");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            log.warn("AI task {} timed out", task.getTaskNo());
        }
    }
}

