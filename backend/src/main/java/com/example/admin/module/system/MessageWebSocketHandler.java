package com.example.admin.module.system;

import com.example.admin.security.JwtUtil;
import com.example.admin.security.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;

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

    private Long authenticate(WebSocketSession session) {
        String token = tokenFromQuery(session.getUri());
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            if (!tokenService.hasValidAccessToken(claims.getId())) {
                return null;
            }
            Long userId = Long.valueOf(claims.getSubject());
            session.getAttributes().put("userId", userId);
            return userId;
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String tokenFromQuery(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            if (part.startsWith("token=")) {
                return part.substring("token=".length());
            }
        }
        return null;
    }
}
