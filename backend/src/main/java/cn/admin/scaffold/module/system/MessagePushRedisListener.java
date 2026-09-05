package cn.admin.scaffold.module.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class MessagePushRedisListener implements MessageListener {

    private final MessageRealtimeService messageRealtimeService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode root = objectMapper.readTree(
                    new String(message.getBody(), StandardCharsets.UTF_8));
            JsonNode payload = root.get("payload");
            if (payload == null || payload.isNull()) {
                return;
            }
            Object value = objectMapper.treeToValue(payload, Object.class);
            if (root.hasNonNull("userId")) {
                messageRealtimeService.pushLocal(root.get("userId").asLong(), value);
            } else {
                messageRealtimeService.broadcastLocal(value);
            }
        } catch (Exception exception) {
            // 忽略畸形广播（Jackson 解析/推送失败不中断消息消费；JsonProcessingException 为受检异常）
        }
    }
}
