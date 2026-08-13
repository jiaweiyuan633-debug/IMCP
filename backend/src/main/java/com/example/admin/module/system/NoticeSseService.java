package com.example.admin.module.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NoticeSseService {

    private static final long NO_TIMEOUT = 0L;
    private static final int DEFAULT_CONNECTION_LIMIT = 5;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private int connectionLimit = DEFAULT_CONNECTION_LIMIT;

    public NoticeSseService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * R4-1.2：每用户并发连接上限，超限回收最旧连接。默认 5，可用
     * app.notice-sse-max-connections-per-user 覆盖；配置 {@code <=0} 表示不限制。
     * 连接为 NO_TIMEOUT 长连接且无心跳外置上限，单账号可循环开流无限堆积
     * （每连接占用一个异步 Servlet 请求 + SseEmitter），构成资源耗尽面。
     */
    @Value("${app.notice-sse-max-connections-per-user:5}")
    public void setConnectionLimit(int connectionLimit) {
        this.connectionLimit = connectionLimit;
    }

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        enforceConnectionLimit(userId);
        return emitter;
    }

    /** 回收该用户最旧连接直至不超过上限；被回收连接 complete() 释放容器异步线程。 */
    private void enforceConnectionLimit(Long userId) {
        if (connectionLimit <= 0) {
            return;
        }
        List<SseEmitter> list = emitters.get(userId);
        while (list != null && list.size() > connectionLimit) {
            SseEmitter oldest = list.get(0);
            remove(userId, oldest);
            try {
                oldest.complete();
            } catch (IllegalStateException ignored) {
                // 连接已被并发关闭；移除逻辑幂等，无需处理
            }
        }
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
