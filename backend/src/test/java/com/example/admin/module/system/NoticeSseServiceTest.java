package com.example.admin.module.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class NoticeSseServiceTest {

    @Test
    void publishAllPublishesOnlyToRedisWithoutLocalDuplicate() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        NoticeSseService service = spy(new NoticeSseService(redisTemplate, new ObjectMapper()));
        service.publishAll("payload");
        // 本地投递交给 Redis 监听器（含本实例），发布方不再重复投递
        verify(redisTemplate).convertAndSend("notice:sse", "\"payload\"");
        verify(service, never()).publishLocal(any());
    }

    @Test
    void publishAllFallsBackToLocalWhenRedisDown() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new DataAccessResourceFailureException("down"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());
        NoticeSseService service = spy(new NoticeSseService(redisTemplate, new ObjectMapper()));
        service.publishAll("payload");
        verify(service).publishLocal("payload");
    }

    @Test
    void broadcastFramesAllAliveConnectionsForUser() {
        NoticeSseService service = new NoticeSseService(mock(StringRedisTemplate.class), new ObjectMapper());
        service.connect(1L);
        service.connect(1L);

        AtomicInteger frames = new AtomicInteger();
        service.broadcast(1L, emitter -> frames.incrementAndGet());

        assertThat(frames.get()).isEqualTo(2);
    }

    @Test
    void broadcastSweepsDeadConnectionWithoutAffectingAliveOnes() {
        NoticeSseService service = new NoticeSseService(mock(StringRedisTemplate.class), new ObjectMapper());
        SseEmitter dead = service.connect(1L);
        service.connect(1L);

        // 目标连接发送失败（僵死/代理已断）→ 仅回收该连接，其余连接不受影响
        service.broadcast(1L, emitter -> {
            if (emitter == dead) {
                throw new IOException("broken pipe");
            }
        });

        AtomicInteger frames = new AtomicInteger();
        service.broadcast(1L, emitter -> frames.incrementAndGet());
        assertThat(frames.get()).isEqualTo(1);
    }
}
