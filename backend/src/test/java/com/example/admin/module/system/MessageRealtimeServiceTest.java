package com.example.admin.module.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageRealtimeServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final NoticeSseService noticeSseService = mock(NoticeSseService.class);
    private final MessageWebSocketService webSocketService = mock(MessageWebSocketService.class);
    private final MessageRealtimeService service =
            new MessageRealtimeService(noticeSseService, webSocketService, redisTemplate, new ObjectMapper());

    private static Map<String, Object> payload() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "MESSAGE");
        map.put("title", "t");
        return map;
    }

    @Test
    void pushToUserPublishesOnlyToRedisWithoutLocalDuplicate() {
        service.pushToUser(1L, payload());
        verify(redisTemplate).convertAndSend(anyString(), anyString());
        // 本地投递交给 Redis 监听器，发布方不再重复投递
        verify(noticeSseService, never()).publish(anyLong(), any());
        verify(webSocketService, never()).sendToUser(anyLong(), any());
    }

    @Test
    void pushToUserFallsBackToLocalWhenRedisDown() {
        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        service.pushToUser(1L, payload());
        verify(noticeSseService).publish(1L, payload());
        verify(webSocketService).sendToUser(1L, payload());
    }

    @Test
    void broadcastPublishesOnlyToRedisWithoutLocalDuplicate() {
        service.broadcast(payload());
        verify(redisTemplate).convertAndSend(anyString(), anyString());
        verify(noticeSseService, never()).publishLocal(any());
        verify(webSocketService, never()).broadcast(any());
    }

    @Test
    void onMessagePushDispatchesUserEventToRedis() {
        service.onMessagePush(new MessagePushEvent(2L, payload()));
        verify(redisTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void onMessagePushDispatchesBroadcastEventToRedis() {
        service.onMessagePush(new MessagePushEvent(null, payload()));
        verify(redisTemplate).convertAndSend(anyString(), anyString());
    }
}
