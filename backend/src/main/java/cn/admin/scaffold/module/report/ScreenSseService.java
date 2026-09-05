package cn.admin.scaffold.module.report;

import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.report.vo.ReportScreenVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据大屏 SSE 实时推送：按租户维护在线连接，定时（默认 30s）计算该租户大屏快照并广播。
 * 调度线程无请求上下文，推前按租户设置 {@link TenantContext}，完成后清理，避免租户数据串台。
 * 多副本部署时各实例各自推送，前端以最新快照覆盖，数据一致无副作用。
 */
@Slf4j
@Service
public class ScreenSseService {

    private static final long NO_TIMEOUT = 0L;
    private static final int DEFAULT_CONNECTION_LIMIT = 5;

    private final ReportService reportService;
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private int connectionLimit = DEFAULT_CONNECTION_LIMIT;

    public ScreenSseService(ReportService reportService, JdbcTemplate jdbcTemplate) {
        this.reportService = reportService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每租户并发连接上限，超限回收最旧连接。默认 5，可用
     * app.screen.max-connections-per-tenant 覆盖；配置 {@code <=0} 表示不限制。
     * 连接为 NO_TIMEOUT 长连接且回收仅依赖 30s 定时广播的发送失败，单账号可循环
     * 取票开流无限堆积（每连接占用一个异步 Servlet 请求 + SseEmitter，且放大每轮
     * 大屏快照聚合与推送开销），构成资源耗尽面。
     */
    @Value("${app.screen.max-connections-per-tenant:5}")
    public void setConnectionLimit(int connectionLimit) {
        this.connectionLimit = connectionLimit;
    }

    /** 票据已校验：按用户归属租户挂接连接（stream 请求无 JWT，租户需按 userId 解析）。 */
    public SseEmitter connect(Long userId) {
        Long tenantId = resolveTenant(userId);
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(tenantId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(error -> remove(tenantId, emitter));
        enforceConnectionLimit(tenantId);
        return emitter;
    }

    /** 回收该租户最旧连接直至不超过上限；被回收连接 complete() 释放容器异步线程。 */
    private void enforceConnectionLimit(Long tenantId) {
        if (connectionLimit <= 0) {
            return;
        }
        List<SseEmitter> list = emitters.get(tenantId);
        while (list != null && list.size() > connectionLimit) {
            SseEmitter oldest = list.get(0);
            remove(tenantId, oldest);
            try {
                oldest.complete();
            } catch (IllegalStateException ignored) {
                // 连接已被并发关闭；移除逻辑幂等，无需处理
            }
        }
    }

    /** 测试接缝：该租户当前在线连接数（只读观测，不泄漏连接引用）。包内可见。 */
    int connectionCount(Long tenantId) {
        List<SseEmitter> list = emitters.get(tenantId);
        return list == null ? 0 : list.size();
    }

    @Scheduled(fixedDelayString = "${app.screen.push-interval-ms:30000}")
    public void scheduledBroadcast() {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.keySet().forEach(this::pushNow);
    }

    public void pushNow(Long tenantId) {
        List<SseEmitter> list = emitters.get(tenantId);
        if (list == null || list.isEmpty()) {
            return;
        }
        TenantContext.setTenantId(tenantId);
        try {
            ReportScreenVo snapshot = reportService.screen();
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("screen").data(snapshot));
                } catch (IOException | IllegalStateException exception) {
                    remove(tenantId, emitter);
                }
            }
        } catch (RuntimeException exception) {
            log.warn("数据大屏快照计算失败，跳过本轮推送: {}", exception.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveTenant(Long userId) {
        try {
            Long tenantId = jdbcTemplate.queryForObject(
                    "SELECT tenant_id FROM sys_user WHERE id = ? AND deleted = 0", Long.class, userId);
            return tenantId == null ? Long.valueOf(1L) : tenantId;
        } catch (EmptyResultDataAccessException exception) {
            return 1L;
        }
    }

    private void remove(Long tenantId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(tenantId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(tenantId);
            }
        }
    }
}
