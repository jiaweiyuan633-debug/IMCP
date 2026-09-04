package cn.admin.scaffold.module.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
            // R4-1.10：Redis 频道消息携带权威目标租户信封（发布线程租户），
            // 各实例据此过滤本地连接，公告内容不再跨租户实时泄露
            NoticeSseService.NoticeBroadcast broadcast = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    NoticeSseService.NoticeBroadcast.class);
            noticeSseService.publishLocal(broadcast.tenantId(), broadcast.payload());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            // ignore malformed broadcast
        }
    }
}
