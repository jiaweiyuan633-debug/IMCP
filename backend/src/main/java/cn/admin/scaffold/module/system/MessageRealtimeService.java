package cn.admin.scaffold.module.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    /**
     * 统一推送入口：userId 非空推送单用户，否则广播。
     * 仅在与业务相同的数据库事务提交后执行，事务回滚时不推送，保证消息与推送一致。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessagePush(MessagePushEvent event) {
        if (event.userId() != null) {
            pushToUser(event.userId(), event.payload());
        } else {
            broadcast(event.payload());
        }
    }

    /**
     * 仅通过 Redis 频道广播，由各实例的 {@link MessagePushRedisListener} 在本地投递，
     * 避免本实例「本地推送 + Redis 回环推送」造成重复投递。
     */
    public void pushToUser(Long userId, Object payload) {
        publishRedis(userId, payload);
    }

    public void pushLocal(Long userId, Object payload) {
        noticeSseService.publish(userId, payload);
        messageWebSocketService.sendToUser(userId, payload);
    }

    public void broadcast(Object payload) {
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
            // Redis 不可用时降级为本实例本地投递，保证不丢推送（多副本下不跨实例，但优于丢失）
            log.warn("消息推送到 Redis 失败，降级本地推送", exception);
            if (userId != null) {
                pushLocal(userId, payload);
            } else {
                broadcastLocal(payload);
            }
        }
    }
}
