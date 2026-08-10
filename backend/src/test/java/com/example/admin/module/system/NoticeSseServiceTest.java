package com.example.admin.module.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

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
}
