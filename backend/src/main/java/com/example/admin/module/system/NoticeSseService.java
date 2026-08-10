package com.example.admin.module.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NoticeSseService {

    private static final long NO_TIMEOUT = 0L;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NoticeSseService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        return emitter;
    }

    /**
     * 仅通过 Redis 频道广播，由各实例（含本实例）的 {@link NoticeSseRedisListener} 在本地投递，
     * 避免本实例「本地推送 + Redis 回环推送」造成重复投递。Redis 不可用时降级为本实例本地推送。
     */
    public void publishAll(Object payload) {
        try {
            redisTemplate.convertAndSend("notice:sse", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException | DataAccessException exception) {
            log.warn("公告广播到 Redis 失败，降级本地推送", exception);
            publishLocal(payload);
        }
    }

    public void publishLocal(Object payload) {
        emitters.forEach((userId, list) -> publish(userId, payload));
    }

    public void publish(Long userId, Object payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notice").data(payload));
            } catch (IOException | IllegalStateException exception) {
                remove(userId, emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
