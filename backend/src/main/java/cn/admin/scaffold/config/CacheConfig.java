package cn.admin.scaffold.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /** 默认 TTL 上的随机抖动窗口：30min ± 3min（±10%）。 */
    private static final Duration TTL_JITTER_WINDOW = Duration.ofMinutes(3);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();
        // 批8e：TTL 抖动写入器，避免同一缓存名下所有 key 同刻过期形成雪崩
        RedisCacheWriter writer = new JitterTtlCacheWriter(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                TTL_JITTER_WINDOW);
        return RedisCacheManager.builder(connectionFactory)
                .cacheWriter(writer)
                .cacheDefaults(configuration)
                .build();
    }
}

