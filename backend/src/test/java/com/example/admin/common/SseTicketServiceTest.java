package com.example.admin.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SseTicketServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = createValueOps();

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> createValueOps() {
        return mock(ValueOperations.class);
    }

    SseTicketServiceTest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void issueStoresUserIdWithShortTtl() {
        SseTicketService service = new SseTicketService(redisTemplate);
        String ticket = service.issue(7L);
        assertFalse(ticket.isBlank());
        verify(valueOps).set(eq("sse:ticket:" + ticket), eq("7"), any(Duration.class));
    }

    @Test
    void consumeReturnsUserIdOnce() {
        when(valueOps.get("sse:ticket:abc")).thenReturn("42");
        SseTicketService service = new SseTicketService(redisTemplate);
        assertEquals(42L, service.consume("abc"));
        verify(redisTemplate).delete("sse:ticket:abc");
        // ticket 一次性：已删除后二次消费返回 null
        when(valueOps.get("sse:ticket:abc")).thenReturn(null);
        assertNull(service.consume("abc"));
    }

    @Test
    void consumeRejectsBlankOrUnknownTicket() {
        SseTicketService service = new SseTicketService(redisTemplate);
        assertNull(service.consume(null));
        assertNull(service.consume("   "));
        when(valueOps.get("sse:ticket:unknown")).thenReturn(null);
        assertNull(service.consume("unknown"));
    }

    @Test
    void consumeRejectsNonNumericUserId() {
        when(valueOps.get("sse:ticket:bad")).thenReturn("not-a-number");
        SseTicketService service = new SseTicketService(redisTemplate);
        assertNull(service.consume("bad"));
    }
}
