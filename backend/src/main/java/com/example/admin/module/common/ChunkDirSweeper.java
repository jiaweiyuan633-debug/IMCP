package com.example.admin.module.common;

import com.example.admin.common.ScheduledTaskLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 分片临时目录清理器（R4-1.16）：周期清扫 chunk 目录下超龄孤儿分片目录，回收中断/放弃上传
 * 遗留的磁盘空间。磁盘泄漏面：临时分片只在 complete 成功或确定失败时清理，Redis 任务 TTL
 * 只管元数据、不管磁盘。多副本通过 {@link ScheduledTaskLock} 互斥，任一时刻仅一个实例清扫；
 * 删除以「最后一次写入距今超过 {@link ChunkFileService#TASK_TTL} 加宽限」判龄，活动上传不会被误清。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkDirSweeper {

    private static final String SWEEP_LOCK_KEY = "chunk-dir-sweep";
    private static final Duration SWEEP_LOCK_TTL = Duration.ofMinutes(5);

    private final ScheduledTaskLock taskLock;
    private final ChunkFileService chunkFileService;

    @Scheduled(fixedDelayString = "${app.chunk-dir-sweep.interval-ms:1800000}", initialDelay = 60_000)
    public void sweep() {
        if (!taskLock.tryLock(SWEEP_LOCK_KEY, SWEEP_LOCK_TTL)) {
            return;
        }
        try {
            int removed = chunkFileService.sweepExpiredDirs();
            if (removed > 0) {
                log.info("分片临时目录清理完成, removed={}", removed);
            }
        } finally {
            taskLock.unlock(SWEEP_LOCK_KEY);
        }
    }
}
