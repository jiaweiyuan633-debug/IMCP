package com.example.admin.security;

import com.example.admin.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    private StringRedisTemplate redisTemplate;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        tokenService = new TokenService(redisTemplate, mock(JwtProperties.class), new ObjectMapper());
    }

    @Test
    void deleteCacheKeyAllowsWhitelistedPrefix() {
        tokenService.deleteCacheKey("login:online:abc");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(keyCaptor.capture());
        assertEquals("login:online:abc", keyCaptor.getValue());
    }

    @Test
    void deleteCacheKeyRejectsSecurityCriticalKey() {
        assertThrows(BusinessException.class, () -> tokenService.deleteCacheKey("login:token:abc"));
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void deleteCacheKeySupportsGlobWithinWhitelist() {
        when(redisTemplate.keys("auth:perms:*")).thenReturn(Set.of("auth:perms:1", "auth:perms:2"));
        tokenService.deleteCacheKey("auth:perms:*");
        verify(redisTemplate).delete(anyCollection());
    }

    @Test
    void evictUserPermissionsDeletesSingleKey() {
        tokenService.evictUserPermissions(7L);
        verify(redisTemplate).delete("auth:perms:7");
    }

    @Test
    void evictUserPermissionsIgnoresNull() {
        tokenService.evictUserPermissions(null);
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void evictPermissionsByUserIdsDeletesEachKey() {
        tokenService.evictPermissionsByUserIds(List.of(1L, 2L));
        verify(redisTemplate).delete(List.of("auth:perms:1", "auth:perms:2"));
    }

    @Test
    void evictPermissionsByUserIdsSkipsEmpty() {
        tokenService.evictPermissionsByUserIds(List.of());
        verify(redisTemplate, never()).delete(anyCollection());
    }

    // ---------- R4-1.12：失效时机修正——事务提交后删除，杜绝并发重缓存旧权限竞态 ----------

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 活跃事务下，失效被推迟到 afterCommit，提交前不触碰 Redis。 */
    @Test
    void evictUserPermissionsAfterCommitDefersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        tokenService.evictUserPermissionsAfterCommit(7L);
        verify(redisTemplate, never()).delete(any(String.class));

        runAfterCommit();
        verify(redisTemplate).delete("auth:perms:7");
    }

    /** 无事务上下文（非事务调用点）退化为立即失效，不丢失失效语义。 */
    @Test
    void evictUserPermissionsAfterCommitImmediateWithoutTransaction() {
        tokenService.evictUserPermissionsAfterCommit(7L);
        verify(redisTemplate).delete("auth:perms:7");
    }

    /** 批量版同样推迟到提交后，且 keys 列表在提交时一次性删除。 */
    @Test
    void evictPermissionsByUserIdsAfterCommitDefersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        tokenService.evictPermissionsByUserIdsAfterCommit(List.of(1L, 2L));
        verify(redisTemplate, never()).delete(anyCollection());

        runAfterCommit();
        verify(redisTemplate).delete(List.of("auth:perms:1", "auth:perms:2"));
    }

    /** 空集合不注册事务同步、不做任何删除。 */
    @Test
    void evictPermissionsByUserIdsAfterCommitSkipsEmpty() {
        TransactionSynchronizationManager.initSynchronization();

        tokenService.evictPermissionsByUserIdsAfterCommit(List.of());
        verify(redisTemplate, never()).delete(anyCollection());
    }

    // ---------- R4-1.31：菜单变更全量失效——提交后清空全部权限缓存 ----------

    @Test
    void evictAllPermissionsAfterCommitDefersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(redisTemplate.keys("auth:perms:*")).thenReturn(Set.of("auth:perms:1", "auth:perms:2"));

        tokenService.evictAllPermissionsAfterCommit();
        verify(redisTemplate, never()).delete(anyCollection());

        runAfterCommit();
        verify(redisTemplate).delete(anyCollection());
    }

    @Test
    void evictAllPermissionsAfterCommitImmediateWithoutTransaction() {
        when(redisTemplate.keys("auth:perms:*")).thenReturn(Set.of("auth:perms:3"));

        tokenService.evictAllPermissionsAfterCommit();
        verify(redisTemplate).delete(anyCollection());
    }

    // ---------- R4-1.35（批8e）：权限缓存 TTL 抖动——避免登录高峰集中过期雪崩 ----------

    /** 多次写入（覆盖随机分布），每次 TTL 都必须落在 [基础 30min, 基础+抖动上限 35min]。 */
    @Test
    void cachePermissionsAppliesTtlJitterWithinBounds() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        for (int i = 0; i < 200; i++) {
            tokenService.cachePermissions(7L, List.of("a", "b"));
        }
        verify(valueOps, times(200)).set(eq("auth:perms:7"), eq("a,b"), ttlCaptor.capture());

        for (Duration ttl : ttlCaptor.getAllValues()) {
            assertTrue(ttl.compareTo(Duration.ofMinutes(30)) >= 0, "TTL 不得低于基础 30min");
            assertTrue(ttl.compareTo(Duration.ofMinutes(35)) <= 0, "TTL 不得超过 30min + 5min 抖动");
        }
    }

    /** 同 key 连续写入，TTL 应随抖动变化（至少出现两种不同取值），证明随机偏移真实生效。 */
    @Test
    void cachePermissionsJitterYieldsVariedTtl() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        for (int i = 0; i < 100; i++) {
            tokenService.cachePermissions(7L, List.of("a"));
        }
        verify(valueOps, times(100)).set(eq("auth:perms:7"), eq("a"), ttlCaptor.capture());
        long distinct = ttlCaptor.getAllValues().stream().map(Duration::toMillis).distinct().count();
        assertTrue(distinct > 1, "TTL 抖动应产生多种取值");
    }

    private void runAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
            }
        }
    }
}
