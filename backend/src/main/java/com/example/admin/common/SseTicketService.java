package com.example.admin.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SseTicketService {

    private static final String TICKET_PREFIX = "sse:ticket:";
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public String issue(Long userId) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(TICKET_PREFIX + ticket, String.valueOf(userId), TICKET_TTL);
        return ticket;
    }

    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String key = TICKET_PREFIX + ticket;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            return null;
        }
        redisTemplate.delete(key);
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
