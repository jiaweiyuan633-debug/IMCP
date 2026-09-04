package cn.admin.scaffold.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledTaskLockTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = createValueOps();

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> createValueOps() {
        return mock(ValueOperations.class);
    }

    ScheduledTaskLockTest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void tryLockAcquiresWhenNotHeldByOthers() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        assertTrue(new ScheduledTaskLock(redisTemplate).tryLock("task-a", Duration.ofSeconds(10)));
    }

    @Test
    void tryLockRejectsWhenHeldByAnotherInstance() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        assertFalse(new ScheduledTaskLock(redisTemplate).tryLock("task-a", Duration.ofSeconds(10)));
    }

    @Test
    void tryLockUsesPrefixedKeyWithTtlAndOwnerToken() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        ScheduledTaskLock lock = new ScheduledTaskLock(redisTemplate);
        lock.tryLock("task-b", Duration.ofSeconds(25));
        // 值为持锁者令牌（随机 UUID），而非固定常量，用于解锁时比对
        verify(valueOps).setIfAbsent(eq("sched:lock:task-b"), anyString(), eq(Duration.ofSeconds(25)));
    }

    @Test
    void unlockUsesLuaCompareAndDelete() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        ScheduledTaskLock lock = new ScheduledTaskLock(redisTemplate);
        assertTrue(lock.tryLock("task-c", Duration.ofSeconds(10)));
        lock.unlock("task-c");
        // 解锁通过 Lua 脚本原子比对持锁者令牌后删除，杜绝误删他人锁
        verify(redisTemplate).execute(any(), eq(List.of("sched:lock:task-c")), any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void unlockWithoutOwningLockDoesNothing() {
        new ScheduledTaskLock(redisTemplate).unlock("task-d");
        verify(redisTemplate, never()).execute(any(), any(), any());
        verify(redisTemplate, never()).delete(anyString());
    }
}
