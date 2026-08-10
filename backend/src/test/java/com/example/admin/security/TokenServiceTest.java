package com.example.admin.security;

import com.example.admin.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

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
}
