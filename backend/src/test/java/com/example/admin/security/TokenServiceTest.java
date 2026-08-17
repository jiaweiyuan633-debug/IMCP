package com.example.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh Token 原子消费单测（R4-1.44 批次17）。
 *
 * <p>verify GETDEL 单命令原子语义：既返回旧值又删除，杜绝 hasKey+delete 两步在并发下
 * 均通过存在性检查导致的轮换失效。
 */
class TokenServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisConnection connection = mock(RedisConnection.class);
    private final RedisStringCommands commands = mock(RedisStringCommands.class);
    private final TokenService tokenService = new TokenService(redisTemplate, new JwtProperties(), new ObjectMapper());

    private void stubExecuteToDelegate() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<byte[]> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(connection.stringCommands()).thenReturn(commands);
    }

    @Test
    void consumeReturnsStoredUserIdAndDeletesAtomically() {
        stubExecuteToDelegate();
        byte[] key = "login:refresh:rt-1".getBytes(StandardCharsets.UTF_8);
        when(commands.getDel(key)).thenReturn("10".getBytes(StandardCharsets.UTF_8));

        String userId = tokenService.consumeRefreshToken("rt-1");

        assertThat(userId).isEqualTo("10");
        // 单命令 GETDEL（原子删除），而非 get + delete 两步
        verify(commands).getDel(key);
    }

    @Test
    void consumeReturnsNullWhenAlreadyConsumed() {
        stubExecuteToDelegate();
        when(commands.getDel(any(byte[].class))).thenReturn(null);

        assertThat(tokenService.consumeRefreshToken("rt-2")).isNull();
    }

    // ---------- 批次2（R4-1.48）：角色缓存（与权限缓存同 TTL 抖动、同失效时机） ----------

    @Test
    void cacheRolesThenGetReturnsCommaJoinedRoles() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:roles:1")).thenReturn("admin,ops");

        assertThat(tokenService.getCachedRoles(1L)).containsExactly("admin", "ops");
    }

    @Test
    void getCachedRolesMissReturnsNull() {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("auth:roles:2")).thenReturn(null);

        assertThat(tokenService.getCachedRoles(2L)).isNull();
    }
}
