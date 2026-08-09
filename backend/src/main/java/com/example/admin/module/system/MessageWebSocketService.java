package com.example.admin.module.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<WebSocketSession>> sessions =
            new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(session);
        send(userId, java.util.Map.of("type", "CONNECTED", "message", "连接成功"));
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
