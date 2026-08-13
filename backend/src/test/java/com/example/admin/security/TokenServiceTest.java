package com.example.admin.security;

import com.example.admin.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    private void runAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (synchronizations != null) {
            for (TransactionSynchronization synchronization : synchronizations) {
                synchronization.afterCommit();
            }
        }
    }
}
