package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.system.entity.SysNoticeDO;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NoticeSseService {

    private static final long NO_TIMEOUT = 0L;
    private static final int DEFAULT_CONNECTION_LIMIT = 5;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Connection>> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SysUserMapper userMapper;
    private int connectionLimit = DEFAULT_CONNECTION_LIMIT;

    public NoticeSseService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, SysUserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    /** 一条 SSE 连接及其所属租户：广播按租户过滤，公告内容不跨租户实时泄露。 */
    record Connection(SseEmitter emitter, Long tenantId) {}

    /** Redis 频道消息信封：携带权威目标租户（发布线程租户），接收端据此过滤，不信任 payload 内字段。 */
    record NoticeBroadcast(Long tenantId, SysNoticeDO payload) {}

    /**
     * 每用户并发连接上限，超限回收最旧连接。默认 5，可用
     * app.notice-sse-max-connections-per-user 覆盖；配置 {@code <=0} 表示不限制。
     * 连接为 NO_TIMEOUT 长连接且无心跳外置上限，单账号可循环开流无限堆积
     * （每连接占用一个异步 Servlet 请求 + SseEmitter），构成资源耗尽面。
     */
    @Value("${app.notice-sse-max-connections-per-user:5}")
    public void setConnectionLimit(int connectionLimit) {
        this.connectionLimit = connectionLimit;
    }

    /**
     * 建立公告实时推送流。SSE 端点为 permitAll + 一次性票据鉴权，请求线程租户恒为
     * 默认 1（TenantFilter 不信任请求头），连接所属租户以用户库表为权威来源定位
     * （与 AI 任务 SSE 同一模式）；用户已删除/不存在则拒绝建连——否则无法确定
     * 租户，广播过滤无从谈起。
     */
    public SseEmitter connect(Long userId) {
        SysUserDO user = userMapper.selectByIdIgnoreTenant(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        Connection connection = new Connection(emitter, user.getTenantId());
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(connection);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        enforceConnectionLimit(userId);
        return emitter;
    }

    /** 回收该用户最旧连接直至不超过上限；被回收连接 complete() 释放容器异步线程。 */
    private void enforceConnectionLimit(Long userId) {
        if (connectionLimit <= 0) {
            return;
        }
        List<Connection> list = emitters.get(userId);
        while (list != null && list.size() > connectionLimit) {
            Connection oldest = list.get(0);
            remove(userId, oldest.emitter());
            try {
                oldest.emitter().complete();
            } catch (IllegalStateException ignored) {
                // 连接已被并发关闭；移除逻辑幂等，无需处理
            }
        }
    }

    /**
     * 按租户广播公告。
     * <p>仅通过 Redis 频道广播，由各实例（含本实例）的 {@link NoticeSseRedisListener} 在本地投递，
     * 避免本实例「本地推送 + Redis 回环推送」造成重复投递。Redis 不可用时降级为本实例本地推送。
     *
     * @param tenantId 目标租户，来自发布线程 {@code TenantContext}——公告在库内按租户隔离，
     *                 实时推送必须同租户过滤，否则租户 A 发布的公告会泄给所有在线租户连接
     *                 （列表/详情走租户拦截器隔离，推送通道不能成为绕过面）
     */
    public void publishAll(Long tenantId, SysNoticeDO payload) {
        try {
            String message = objectMapper.writeValueAsString(new NoticeBroadcast(tenantId, payload));
            redisTemplate.convertAndSend("notice:sse", message);
        } catch (JsonProcessingException | DataAccessException exception) {
            log.warn("公告广播到 Redis 失败，降级本地推送", exception);
            publishLocal(tenantId, payload);
        }
    }

    /** 按租户过滤投递：仅推送给连接所属租户等于目标租户的在线连接。 */
    public void publishLocal(Long tenantId, Object payload) {
        emitters.forEach((userId, connections) -> {
            for (Connection connection : connections) {
                if (!tenantId.equals(connection.tenantId())) {
                    continue;
                }
                try {
                    connection.emitter().send(SseEmitter.event().name("notice").data(payload));
                } catch (Exception exception) {
                    remove(userId, connection.emitter());
                }
            }
        });
    }

    /** 全量广播（不按租户过滤）：站内信系统广播等全局消息使用；公告实时推送须走按租户版本。 */
    public void publishLocal(Object payload) {
        emitters.forEach((userId, list) ->
                broadcast(userId, emitter -> emitter.send(SseEmitter.event().name("notice").data(payload))));
    }

    public void publish(Long userId, Object payload) {
        broadcast(userId, emitter -> emitter.send(SseEmitter.event().name("notice").data(payload)));
    }

    /**
     * 长连接心跳。公告为事件驱动——无新公告时连接长时间空闲，反向代理
     * （Nginx proxy_read_timeout 默认 60s）会切断空闲 SSE 连接导致推送静默失效；
     * 且客户端异常断开/网络分区时容器回调可能不触发，僵死连接随运行时间在内存中堆积。
     * 心跳注释帧定期保活，发送失败即回收该连接。间隔可配置，默认 30s。
     */
    @Scheduled(fixedDelayString = "${app.notice-sse-heartbeat-ms:30000}")
    public void heartbeat() {
        emitters.forEach((userId, list) ->
                broadcast(userId, emitter -> emitter.send(SseEmitter.event().comment("hb"))));
    }

    /** 向某用户全部连接发送一帧，发送失败即回收该连接（连接已断）。包内可见：单元测试直接驱动。 */
    void broadcast(Long userId, SseSender sender) {
        List<Connection> list = emitters.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Connection connection : list) {
            try {
                sender.send(connection.emitter());
            } catch (Exception exception) {
                remove(userId, connection.emitter());
            }
        }
    }

    /** 发送函数测试接缝：由 {@link #broadcast} 捕获异常并回收失败连接。 */
    @FunctionalInterface
    interface SseSender {
        void send(SseEmitter emitter) throws Exception;
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<Connection> list = emitters.get(userId);
        if (list != null) {
            list.removeIf(connection -> connection.emitter().equals(emitter));
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
