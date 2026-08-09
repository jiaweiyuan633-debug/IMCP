package com.example.admin.module.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRealtimeService {

    private static final String PUSH_CHANNEL = "message:push";

    private final NoticeSseService noticeSseService;
    private final MessageWebSocketService messageWebSocketService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void pushToUser(Long userId, Object payload) {
        pushLocal(userId, payload);
        publishRedis(userId, payload);
    }

    public void pushLocal(Long userId, Object payload) {
        noticeSseService.publish(userId, payload);
        messageWebSocketService.sendToUser(userId, payload);
    }

    public void broadcast(Object payload) {
        noticeSseService.publishLocal(payload);
        messageWebSocketService.broadcast(payload);
        publishRedis(null, payload);
    }

    public void broadcastLocal(Object payload) {
        noticeSseService.publishLocal(payload);
        messageWebSocketService.broadcast(payload);
    }

    private void publishRedis(Long userId, Object payload) {
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("userId", userId);
            envelope.put("payload", payload);
            redisTemplate.convertAndSend(PUSH_CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException | DataAccessException exception) {
            log.warn("消息推送到 Redis 失败", exception);
        }
    }
}
