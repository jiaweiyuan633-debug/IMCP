package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.SseTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final MessageWebSocketService messageWebSocketService;
    private final SseTicketService sseTicketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = authenticate(session);
        if (userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("未授权"));
            } catch (Exception exception) {
                log.debug("关闭未授权 WebSocket 失败", exception);
            }
            return;
        }
        messageWebSocketService.add(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端心跳或业务查询在后续批次扩展，这里保持连接可用
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = session.getAttributes().get("userId") instanceof Number number
                ? number.longValue()
                : null;
        if (userId != null) {
            messageWebSocketService.remove(userId, session);
        }
    }

    /**
     * 鉴权改用短期一次性 ticket（与 SSE 同机制，Redis 存 userId、60s 过期），
     * 不再允许在 URL 上携带长期 access token，避免凭证进入代理/访问日志。
     */
    private Long authenticate(WebSocketSession session) {
        String ticket = ticketFromQuery(session.getUri());
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        Long userId = sseTicketService.consume(ticket);
        if (userId == null) {
            return null;
        }
        session.getAttributes().put("userId", userId);
        return userId;
    }

    private String ticketFromQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            if (part.startsWith("ticket=")) {
                return part.substring("ticket=".length());
            }
        }
        return null;
    }
}
