package cn.admin.scaffold.module.system;

import java.util.Map;

/**
 * 消息实时推送事件：由消息发送方在业务事务内发布，userId 为 null 表示广播。
 * {@link MessageRealtimeService#onMessagePush} 在事务提交后统一推送到 SSE/WebSocket，
 * 避免业务事务回滚时仍推送「幽灵消息」。
 */
public record MessagePushEvent(Long userId, Map<String, Object> payload) {
}
