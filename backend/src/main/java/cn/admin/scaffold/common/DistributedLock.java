package cn.admin.scaffold.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用分布式锁（Redisson RLock，可重入）。
 *
 * <p>与 {@code ScheduledTaskLock}（仅定时任务互斥）不同，本组件面向业务临界区：
 * 如并发扣减、重复创建、任务抢占等。同一实例内可重入，跨实例由 Redisson 保证互斥。
 * 拿到锁执行期间默认持有至方法结束自动释放；网络分区下 Redisson 看门狗自动续期，
 * 避免锁持有超时被误释放导致的并发穿透。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private static final String KEY_PREFIX = "dist:lock:";

    private final RedissonClient redissonClient;

    /**
     * 加锁执行，拿不到锁抛 {@link BusinessException}（ACQUIRE_LOCK_TIMEOUT）。
     *
     * @param lockKey   业务锁键（同键互斥，建议含业务唯一标识）
     * @param waitTime  最多等待锁的时长，超时视为繁忙
     * @param leaseTime 锁持有租约。传 0 或负值由 Redisson 看门狗自动续期（默认每 10s 续
     *                  30s），适合执行时长不确定的任务；传正值则【固定持有、无看门狗续期】，
     *                  到期自动释放——执行必须保证在该时长内完成，否则锁中途释放会放行并发，
     *                  破坏临界区互斥。
     */
    public <T> T execute(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        RLock lock = redissonClient.getLock(KEY_PREFIX + lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new BusinessException(ResultCode.ACQUIRE_LOCK_TIMEOUT);
            }
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.ACQUIRE_LOCK_TIMEOUT);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 便捷重载：默认最多等 3s、租约交给看门狗自动续期（执行时长不限）。
     *
     * <p>R4-1.30 修正：此前固定 10s 租约且无看门狗续期，执行超过 10s 锁即自动释放，
     * 另一实例可并发进入临界区（如大文件合并）；改为 0 租约启用 Redisson 看门狗，
     * 长任务锁不会中途丢失。
     */
    public <T> T execute(String lockKey, Supplier<T> action) {
        return execute(lockKey, Duration.ofSeconds(3), Duration.ZERO, action);
    }
}
