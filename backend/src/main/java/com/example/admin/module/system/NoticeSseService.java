package com.example.admin.module.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        broadcast(userId, emitter -> emitter.send(SseEmitter.event().name("notice").data(payload)));
    }

    /**
     * R4-1.1：长连接心跳。公告为事件驱动——无新公告时连接长时间空闲，反向代理
     * （Nginx proxy_read_timeout 默认 60s）会切断空闲 SSE 连接导致推送静默失效；
     * 且客户端异常断开/网络分区时容器回调可能不触发，僵死连接随运行时间在内存中堆积。
     * 心跳注释帧定期保活，发送失败即回收该连接。间隔可配置，默认 30s。
     */
    @Scheduled(fixedDelayString = "${app.notice-sse-heartbeat-ms:30000}")
    public void heartbeat() {
        emitters.forEach((userId, list) ->
                broadcast(userId, emitter -> emitter.send(SseEmitter.event().comment("hb"))));
    }

    /** 向某用户全部连接发送一帧，发送失败即回收该连接（连接已断）。包内可见：单元测试直接驱动。 */
    void broadcast(Long userId, SseSender sender) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                sender.send(emitter);
            } catch (Exception exception) {
                remove(userId, emitter);
            }
        }
    }

    /** 发送函数测试接缝：由 {@link #broadcast} 捕获异常并回收失败连接。 */
    @FunctionalInterface
    interface SseSender {
        void send(SseEmitter emitter) throws Exception;
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
