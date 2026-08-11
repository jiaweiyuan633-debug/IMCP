package com.example.admin.module.report;

import com.example.admin.common.TenantContext;
import com.example.admin.module.report.vo.ReportScreenVo;
import lombok.extern.slf4j.Slf4j;
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

    private final ReportService reportService;
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public ScreenSseService(ReportService reportService, JdbcTemplate jdbcTemplate) {
        this.reportService = reportService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 票据已校验：按用户归属租户挂接连接（stream 请求无 JWT，租户需按 userId 解析）。 */
    public SseEmitter connect(Long userId) {
        Long tenantId = resolveTenant(userId);
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.computeIfAbsent(tenantId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(error -> remove(tenantId, emitter));
        return emitter;
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
            return tenantId == null ? 1L : tenantId;
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
