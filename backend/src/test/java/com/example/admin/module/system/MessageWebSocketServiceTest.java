package com.example.admin.module.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** R4-1.14：WebSocket 每用户并发连接上限，超限回收最旧连接。 */
class MessageWebSocketServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageWebSocketService service = new MessageWebSocketService(objectMapper);

    /** mock 会话默认 isOpen()=false，add() 内的 CONNECTED 推送被跳过，不影响连接数断言。 */
    private WebSocketSession session() {
        return mock(WebSocketSession.class);
    }

    @Test
    void addEvictsOldestConnectionWhenPerUserLimitExceeded() throws IOException {
        service.setMaxConnectionsPerUser(2);

        WebSocketSession first = session();
        WebSocketSession second = session();
        WebSocketSession third = session();

        service.add(1L, first);
        service.add(1L, second);
        service.add(1L, third);

        // 超限 → 回收最旧连接，仅保留最近 2 条
        assertThat(service.connectionCount(1L)).isEqualTo(2);
        verify(first).close(CloseStatus.POLICY_VIOLATION.withReason("超出单用户连接数上限"));
        verify(second, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
        verify(third, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
    }

    @Test
    void connectionLimitZeroMeansUnlimited() throws IOException {
        service.setMaxConnectionsPerUser(0);

        service.add(1L, session());
        service.add(1L, session());
        service.add(1L, session());

        // 上限 0 = 不限制，3 条全部保留，无连接被关闭
        assertThat(service.connectionCount(1L)).isEqualTo(3);
    }

    @Test
    void evictedConnectionsAreRemovedAndKeyClearedWhenEmpty() throws IOException {
        service.setMaxConnectionsPerUser(1);

        WebSocketSession first = session();
        service.add(1L, first);
        assertThat(service.connectionCount(1L)).isEqualTo(1);

        // 第二连接触发回收第一条，仅剩新连接
        WebSocketSession second = session();
        service.add(1L, second);
        assertThat(service.connectionCount(1L)).isEqualTo(1);

        // 连接全部关闭后用户条目清理，连接数为 0
        service.remove(1L, second);
        assertThat(service.connectionCount(1L)).isEqualTo(0);
    }
}
