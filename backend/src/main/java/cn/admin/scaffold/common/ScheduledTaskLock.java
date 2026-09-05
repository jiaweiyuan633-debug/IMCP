package cn.admin.scaffold.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务分布式互斥锁（基于 Redis SETNX + TTL + 持锁者令牌）。
 *
 * <p>Spring 的 {@code @Scheduled} 任务不经过 Quartz 集群，多副本部署时每个实例都会执行；
 * 在任务入口获取互斥锁可保证任意时刻仅一个实例执行。锁随 TTL 自动过期，
 * 避免任务异常中断后锁永久残留导致任务饿死。
 *
 * <p>持锁者令牌（owner token）：解锁通过 Lua 脚本比较值后原子删除。若任务执行超过
 * TTL 导致锁提前过期并被其他实例抢占，本实例解锁时不会误删新持锁者的锁，
 * 避免两个实例并发执行同一任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskLock {

    private static final String KEY_PREFIX = "sched:lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ConcurrentHashMap<String, String> ownerTokens = new ConcurrentHashMap<>();

    /**
     * 尝试获取任务锁，获取成功返回 true（此时由调用方负责执行任务）。
     * TTL 应略小于任务执行间隔，确保单次执行异常时下一轮仍可抢占。
     */
    public boolean tryLock(String taskName, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + taskName, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        ownerTokens.put(taskName, token);
        return true;
    }

    /**
     * 释放任务锁。仅当锁值仍为本实例持锁者令牌时才删除，防止锁超时被他人抢占后误删他人的锁。
     * 比较与删除通过 Lua 脚本原子完成；释放失败时锁随 TTL 自动过期，不影响下一轮抢占。
     */
    public void unlock(String taskName) {
        String token = ownerTokens.remove(taskName);
        if (token == null) {
            return;
        }
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(KEY_PREFIX + taskName), token);
        } catch (DataAccessException exception) {
            log.warn("释放任务锁失败（将随 TTL 自动过期）: taskName={}", taskName, exception);
        }
    }
}
