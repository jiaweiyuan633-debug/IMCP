package com.example.admin.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ACCESS_KEY = "login:token:";
    private static final String REFRESH_KEY = "login:refresh:";
    private static final String BLACKLIST_KEY = "login:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;

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
    }

    public void revokeRefreshToken(String refreshJti) {
        redisTemplate.delete(REFRESH_KEY + refreshJti);
    }
}

