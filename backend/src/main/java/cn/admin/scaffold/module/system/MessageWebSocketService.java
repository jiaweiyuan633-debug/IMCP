package cn.admin.scaffold.module.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageWebSocketService {

    private static final int DEFAULT_MAX_CONNECTIONS_PER_USER = 5;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<WebSocketSession>> sessions =
            new ConcurrentHashMap<>();
    private int maxConnectionsPerUser = DEFAULT_MAX_CONNECTIONS_PER_USER;

    /**
     * R4-1.14：每用户并发连接上限，超限回收最旧连接。默认 5，可用
     * app.websocket.max-connections-per-user 覆盖；配置 {@code <=0} 表示不限制。
     * 连接为长连接且本服务无心跳/探测回收，单账号可循环取票开流无限堆积（每连接占用
     * 一条 TCP 连接 + WebSocketSession 对象，且放大每轮消息推送开销），构成资源耗尽面。
     * 回收最旧连接以保留用户当前活跃连接（与 SSE 各通道同款防护）。
     */
    @Value("${app.websocket.max-connections-per-user:5}")
    public void setMaxConnectionsPerUser(int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    public void add(Long userId, WebSocketSession session) {
        List<WebSocketSession> list = sessions.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());
        list.add(session);
        enforceConnectionLimit(userId);
        send(userId, java.util.Map.of("type", "CONNECTED", "message", "连接成功"));
    }

    /** 回收该用户最旧连接直至不超过上限；被回收连接按策略违规关闭，客户端可感知并重连。 */
    private void enforceConnectionLimit(Long userId) {
        if (maxConnectionsPerUser <= 0) {
            return;
        }
        List<WebSocketSession> list = sessions.get(userId);
        while (list != null && list.size() > maxConnectionsPerUser) {
            WebSocketSession oldest = list.get(0);
            remove(userId, oldest);
            try {
                oldest.close(CloseStatus.POLICY_VIOLATION.withReason("超出单用户连接数上限"));
            } catch (Exception ignored) {
                // 连接已被并发关闭（close 幂等/可重试），移除逻辑幂等，无需处理；
                // 留 debug 日志便于排查异常回收路径（批次9·R4-1.55 消除静默吞异常）
                log.debug("关闭超限连接失败 userId={}", userId, ignored);
            }
        }
    }

    /** 测试接缝：该用户当前在线连接数（只读观测，不泄漏连接引用）。包内可见。 */
    int connectionCount(Long userId) {
        List<WebSocketSession> list = sessions.get(userId);
        return list == null ? 0 : list.size();
    }

    public void remove(Long userId, WebSocketSession session) {
        List<WebSocketSession> list = sessions.get(userId);
        if (list != null) {
            list.remove(session);
            if (list.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public void sendToUser(Long userId, Object payload) {
        send(userId, payload);
    }

    public void broadcast(Object payload) {
        sessions.forEach((userId, list) -> send(userId, payload));
    }

    private void send(Long userId, Object payload) {
        List<WebSocketSession> list = sessions.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.warn("序列化 WebSocket 消息失败", exception);
            return;
        }
        for (WebSocketSession session : list) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(text));
                }
            } catch (IOException exception) {
                remove(userId, session);
            }
        }
    }
}
