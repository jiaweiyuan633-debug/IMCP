package com.example.admin.config;

import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存 TTL 抖动写入器（批8e，R4-1.35）。
 *
 * <p>包装默认 {@link RedisCacheWriter}，在每次带 TTL 的写入（put/store/putIfAbsent）时对 TTL
 * 施加 ±jitterWindow 的随机偏移。此前 Spring Cache 缓存（configs/dictData）共用固定 30 分钟
 * TTL：同一缓存名下所有 key 的绝对过期时刻与其写入时刻严格一致，一旦集中回填（如清理缓存后
 * 批量重建），全部 key 会在同一时刻批量过期，形成对数据库的穿透高峰（缓存雪崩）。
 *
 * <p>抖动仅改变各 key 的重建时机，不影响数据正确性——缓存提前/延后过期只是让重建提前或推后
 * 一个随机小窗口。只读/删除/统计操作原样委托。
 */
final class JitterTtlCacheWriter implements RedisCacheWriter {

    private final RedisCacheWriter delegate;
    private final Duration jitterWindow;

    JitterTtlCacheWriter(RedisCacheWriter delegate, Duration jitterWindow) {
        this.delegate = delegate;
        this.jitterWindow = jitterWindow;
    }

    @Override
    public void put(String name, byte[] key, byte[] value, Duration ttl) {
        delegate.put(name, key, value, jittered(ttl));
    }

    @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.store(name, key, value, jittered(ttl));
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
        return delegate.putIfAbsent(name, key, value, jittered(ttl));
    }

    @Override
    public byte[] get(String name, byte[] key) {
        return delegate.get(name, key);
    }

    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
        return delegate.retrieve(name, key, ttl);
    }

    @Override
    public void remove(String name, byte[] key) {
        delegate.remove(name, key);
    }

    @Override
    public void clean(String name, byte[] key) {
        delegate.clean(name, key);
    }

    @Override
    public void clearStatistics(String name) {
        delegate.clearStatistics(name);
    }

    @Override
    public CacheStatistics getCacheStatistics(String name) {
        return delegate.getCacheStatistics(name);
    }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector statisticsCollector) {
        return new JitterTtlCacheWriter(delegate.withStatisticsCollector(statisticsCollector), jitterWindow);
    }

    /** TTL 加 ±jitterWindow 随机偏移；结果钳制为正，避免 0/负 TTL 导致 key 立即失效。 */
    private Duration jittered(Duration ttl) {
        if (ttl == null) {
            return null;
        }
        long windowMillis = jitterWindow.toMillis();
        long offset = ThreadLocalRandom.current().nextLong(-windowMillis, windowMillis + 1);
        Duration result = ttl.plusMillis(offset);
        return result.isZero() || result.isNegative() ? Duration.ofMillis(1) : result;
    }
}
