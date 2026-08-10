package com.example.admin.security;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.monitor.vo.OnlineUserVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ACCESS_KEY = "login:token:";
    private static final String REFRESH_KEY = "login:refresh:";
    private static final String BLACKLIST_KEY = "login:blacklist:";
    private static final String ONLINE_KEY = "login:online:";
    private static final String PERMS_KEY = "auth:perms:";

    // 缓存管理页允许清理的前缀白名单：仅限可自愈的业务缓存；
    // 认证令牌/授权码/限流计数/分布式锁等关键 key 禁止通过缓存页删除，避免造成会话失效或 DoS
    private static final Set<String> CACHE_DELETE_ALLOWED_PREFIXES = Set.of(
            "captcha:", "auth:perms:", "login:online:");

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void clearPermissionCacheOnStartup() {
        evictAllPermissions();
    }

    public void saveAccessToken(String accessJti, String refreshJti) {
        redisTemplate.opsForValue().set(
                ACCESS_KEY + accessJti,
                refreshJti,
                Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
    }

    public void saveRefreshToken(String refreshJti, String userId) {
        redisTemplate.opsForValue().set(
                REFRESH_KEY + refreshJti,
                userId,
                Duration.ofDays(properties.getRefreshTokenExpireDays()));
    }

    public boolean hasValidAccessToken(String accessJti) {
        Boolean exists = redisTemplate.hasKey(ACCESS_KEY + accessJti);
        Boolean blacklisted = redisTemplate.hasKey(BLACKLIST_KEY + accessJti);
        return Boolean.TRUE.equals(exists) && !Boolean.TRUE.equals(blacklisted);
    }

    public boolean hasRefreshToken(String refreshJti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_KEY + refreshJti));
    }

    public void revokeAccessToken(String accessJti) {
        String refreshJti = redisTemplate.opsForValue().get(ACCESS_KEY + accessJti);
        redisTemplate.delete(ACCESS_KEY + accessJti);
        if (refreshJti != null) {
            redisTemplate.delete(REFRESH_KEY + refreshJti);
        }
        redisTemplate.opsForValue().set(
                BLACKLIST_KEY + accessJti,
                "1",
                Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
        removeOnlineUser(accessJti);
    }

    public void revokeRefreshToken(String refreshJti) {
        redisTemplate.delete(REFRESH_KEY + refreshJti);
    }

    public void saveOnlineUser(String accessJti, OnlineUserVo onlineUser) {
        try {
            onlineUser.setLoginTime(LocalDateTime.now());
            redisTemplate.opsForValue().set(
                    ONLINE_KEY + accessJti,
                    objectMapper.writeValueAsString(onlineUser),
                    Duration.ofMinutes(properties.getAccessTokenExpireMinutes()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save online user", exception);
        }
    }

    public void removeOnlineUser(String accessJti) {
        redisTemplate.delete(ONLINE_KEY + accessJti);
    }

    public List<OnlineUserVo> listOnlineUsers() {
        Set<String> keys = redisTemplate.keys(ONLINE_KEY + "*");
        List<OnlineUserVo> onlineUsers = new ArrayList<>(16);
        if (keys == null) {
            return onlineUsers;
        }
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            try {
                OnlineUserVo onlineUser = objectMapper.readValue(value, new TypeReference<>() {
                });
                onlineUser.setTokenId(key.substring(ONLINE_KEY.length()));
                onlineUsers.add(onlineUser);
            } catch (JsonProcessingException ignored) {
                // skip malformed online record
            }
        }
        onlineUsers.sort(Comparator.comparing(OnlineUserVo::getLoginTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return onlineUsers;
    }

    /**
     * 清理缓存 key（支持尾部通配符 *）：仅允许白名单内的可自愈缓存前缀。
     */
    public void deleteCacheKey(String key) {
        if (!StringUtils.hasText(key)
                || CACHE_DELETE_ALLOWED_PREFIXES.stream().noneMatch(key::startsWith)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "仅允许清理白名单缓存前缀：" + String.join(", ", CACHE_DELETE_ALLOWED_PREFIXES));
        }
        if (key.endsWith("*")) {
            Set<String> keys = redisTemplate.keys(key);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } else {
            redisTemplate.delete(key);
        }
    }

    public List<String> getCachedPermissions(Long userId) {
        String value = redisTemplate.opsForValue().get(PERMS_KEY + userId);
        if (value == null || value.isBlank()) {
            return null;
        }
        return List.of(value.split(","));
    }

    public void cachePermissions(Long userId, List<String> perms) {
        redisTemplate.opsForValue().set(
                PERMS_KEY + userId,
                String.join(",", perms),
                Duration.ofMinutes(30));
    }

    public void evictAllPermissions() {
        Set<String> keys = redisTemplate.keys(PERMS_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

