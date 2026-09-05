package cn.admin.scaffold.config;

import cn.admin.scaffold.module.system.NoticeSseRedisListener;
import cn.admin.scaffold.module.system.MessagePushRedisListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RedisMessageConfig {

    private final NoticeSseRedisListener noticeSseRedisListener;
    private final MessagePushRedisListener messagePushRedisListener;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(noticeSseRedisListener, new ChannelTopic("notice:sse"));
        container.addMessageListener(messagePushRedisListener, new ChannelTopic("message:push"));
        return container;
    }
}
