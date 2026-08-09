package com.example.admin.security;

import com.example.admin.module.monitor.vo.OnlineUserVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ACCESS_KEY = "login:token:";
    private static final String REFRESH_KEY = "login:refresh:";
    private static final String BLACKLIST_KEY = "login:blacklist:";
    private static final String ONLINE_KEY = "login:online:";
    private static final String PERMS_KEY = "auth:perms:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;
    private final ObjectMapper objectMapper;

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

    public Optional<String> getAccessTokenValue(String accessJti) {
        String value = redisTemplate.opsForValue().get(ACCESS_KEY + accessJti);
        return Optional.ofNullable(value);
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
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save online user", exception);
        }
    }

    public void removeOnlineUser(String accessJti) {
        redisTemplate.delete(ONLINE_KEY + accessJti);
    }

    public List<OnlineUserVo> listOnlineUsers() {
        Set<String> keys = redisTemplate.keys(ONLINE_KEY + "*");
        List<OnlineUserVo> onlineUsers = new ArrayList<>();
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
            } catch (Exception ignored) {
                // skip malformed online record
            }
        }
        onlineUsers.sort(Comparator.comparing(OnlineUserVo::getLoginTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return onlineUsers;
    }

    public void deleteCacheKey(String key) {
        redisTemplate.delete(key);
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

