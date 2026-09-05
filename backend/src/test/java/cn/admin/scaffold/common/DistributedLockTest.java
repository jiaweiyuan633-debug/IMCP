package cn.admin.scaffold.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DistributedLock 租约语义回归。此前默认重载固定 10s 租约且 Redisson 对正值
 * 租约【不启动看门狗续期】，执行超过 10s 锁自动释放会放行并发（如大文件合并）；修复后
 * 默认 leaseTime=0 启用看门狗自动续期，锁不会因固定租约中途丢失。
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        distributedLock = new DistributedLock(redissonClient);
    }

    @Test
    void defaultOverloadEnablesWatchdogLease() throws Exception {
        when(redissonClient.getLock("dist:lock:file-chunk-complete:u1")).thenReturn(lock);
        // leaseTime=0 → Redisson 看门狗自动续期（每 10s 续 30s），长任务锁不中途释放
        when(lock.tryLock(3000, 0, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = distributedLock.execute("file-chunk-complete:u1", () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(lock).tryLock(3000, 0, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void explicitLeaseTimeIsPassedThrough() throws Exception {
        when(redissonClient.getLock("dist:lock:k")).thenReturn(lock);
        when(lock.tryLock(1000, 5000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        distributedLock.execute("k", Duration.ofSeconds(1), Duration.ofSeconds(5), () -> "ok");

        verify(lock).tryLock(1000, 5000, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }
}
