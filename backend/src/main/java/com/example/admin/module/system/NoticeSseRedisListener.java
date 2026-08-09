package com.example.admin.module.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.admin.module.system.entity.SysNoticeDO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class NoticeSseRedisListener implements MessageListener {

    private final NoticeSseService noticeSseService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SysNoticeDO notice = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    SysNoticeDO.class);
            noticeSseService.publishLocal(notice);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // ignore malformed broadcast
        }
    }
}
