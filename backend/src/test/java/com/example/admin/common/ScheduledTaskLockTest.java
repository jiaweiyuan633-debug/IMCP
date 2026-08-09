package com.example.admin.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
    void tryLockUsesPrefixedKeyWithTtl() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        new ScheduledTaskLock(redisTemplate).tryLock("task-b", Duration.ofSeconds(25));
        verify(valueOps).setIfAbsent("sched:lock:task-b", "1", Duration.ofSeconds(25));
    }

    @Test
    void unlockDeletesPrefixedKey() {
        new ScheduledTaskLock(redisTemplate).unlock("task-a");
        verify(redisTemplate).delete("sched:lock:task-a");
    }
}
