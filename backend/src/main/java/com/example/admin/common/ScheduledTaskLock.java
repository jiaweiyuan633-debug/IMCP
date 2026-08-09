package com.example.admin.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务分布式互斥锁（基于 Redis SETNX + TTL）。
 *
 * <p>Spring 的 {@code @Scheduled} 任务不经过 Quartz 集群，多副本部署时每个实例都会执行；
 * 在任务入口获取互斥锁可保证任意时刻仅一个实例执行。锁随 TTL 自动过期，
 * 避免任务异常中断后锁永久残留导致任务饿死。
 */
@Component
@RequiredArgsConstructor
public class ScheduledTaskLock {

    private static final String KEY_PREFIX = "sched:lock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 尝试获取任务锁，获取成功返回 true（此时由调用方负责执行任务）。
     * TTL 应略小于任务执行间隔，确保单次执行异常时下一轮仍可抢占。
     */
    public boolean tryLock(String taskName, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + taskName, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String taskName) {
        redisTemplate.delete(KEY_PREFIX + taskName);
    }
}
