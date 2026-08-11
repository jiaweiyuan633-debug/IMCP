package com.example.admin.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发件箱投递器：把待投递行路由到对应 {@link OutboxHandler}，成功置终态，
 * 失败按指数退避写回 {@code next_retry_at} 待重试，超限进入终态失败。
 *
 * <p>投递入口有两路，互相兜底：
 * <ul>
 *   <li>事务提交后由 {@link OutboxPublisher#onCommitted} 即时投递（低延迟）；</li>
 *   <li>{@link #pollExpired()} 定时清扫（默认每 30s）兜底即时投递丢失/进程崩溃的场景。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    /** 失败重试的指数退避基线与上限（秒）。 */
    private static final long BACKOFF_BASE_SECONDS = 2L;
    private static final long BACKOFF_MAX_SECONDS = 300L;
    /** 单轮轮询最多处理条数，防止堆积时单次清扫过长。 */
    private static final int POLL_BATCH_SIZE = 50;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_DEAD = 3;

    private final JdbcTemplate jdbcTemplate;
    private final List<OutboxHandler> handlers;

    private final Map<String, OutboxHandler> handlerByTopic = new HashMap<>();

    private OutboxHandler handlerFor(String topic) {
        if (handlerByTopic.isEmpty()) {
            synchronized (handlerByTopic) {
                if (handlerByTopic.isEmpty()) {
                    for (OutboxHandler handler : handlers) {
                        handlerByTopic.putIfAbsent(handler.topic(), handler);
                    }
                }
            }
        }
        return handlerByTopic.get(topic);
    }

    /**
     * 投递单条发件箱行。成功置 1；失败记一次重试并退避（超限置 3）。
     * 本方法幂等：对已成功/终态失败的行直接跳过。
     */
    public void dispatch(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        Row row = queryRow(outboxId);
        if (row == null || row.status == STATUS_SUCCESS || row.status == STATUS_DEAD) {
            return;
        }
        OutboxHandler handler = handlerFor(row.topic);
        if (handler == null) {
            log.warn("发件箱无对应处理器，主题={}，outboxId={}，标记终态失败", row.topic, outboxId);
            jdbcTemplate.update("UPDATE sys_outbox SET status = ?, last_error = ?, updated_at = NOW() WHERE id = ?",
                    STATUS_DEAD, "no handler for topic: " + row.topic, outboxId);
            return;
        }
        boolean ok;
        String error = null;
        try {
            ok = handler.handle(row.payload);
        } catch (Throwable t) {
            ok = false;
            error = truncate(t.getMessage());
            log.warn("发件箱投递失败，topic={}，outboxId={}，retryCount={}", row.topic, outboxId, row.retryCount, t);
        }
        if (ok) {
            jdbcTemplate.update("UPDATE sys_outbox SET status = ?, last_error = NULL, updated_at = NOW() WHERE id = ?",
                    STATUS_SUCCESS, outboxId);
            return;
        }
        int retry = row.retryCount + 1;
        if (retry >= row.maxRetry) {
            jdbcTemplate.update(
                    "UPDATE sys_outbox SET status = ?, retry_count = ?, last_error = ?, updated_at = NOW() WHERE id = ?",
                    STATUS_DEAD, retry, error, outboxId);
            log.error("发件箱重试达上限，topic={}，outboxId={}，转入终态失败", row.topic, outboxId);
            return;
        }
        long backoffSeconds = Math.min(BACKOFF_MAX_SECONDS, BACKOFF_BASE_SECONDS * (1L << (retry - 1)));
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
        jdbcTemplate.update(
                "UPDATE sys_outbox SET status = ?, retry_count = ?, next_retry_at = ?, last_error = ?, updated_at = NOW() WHERE id = ?",
                STATUS_FAILED, retry, Timestamp.valueOf(nextRetry), error, outboxId);
    }

    /** 定时清扫待投递/待重试行（每 30s）。 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void pollExpired() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_outbox WHERE status IN (?, ?) AND (next_retry_at IS NULL OR next_retry_at <= NOW())"
                        + " ORDER BY id LIMIT ?",
                Long.class, STATUS_PENDING, STATUS_FAILED, POLL_BATCH_SIZE);
        if (ids.isEmpty()) {
            return;
        }
        log.info("发件箱轮询命中 {} 条待投递", ids.size());
        for (Long id : ids) {
            dispatch(id);
        }
    }

    private Row queryRow(Long id) {
        RowMapper<Row> rowMapper = this::mapRow;
        List<Row> rows = jdbcTemplate.query(
                "SELECT id, topic, payload, status, retry_count, max_retry FROM sys_outbox WHERE id = ?",
                rowMapper, new Object[]{id});
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Row mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Row(rs.getLong("id"), rs.getString("topic"), rs.getString("payload"),
                rs.getInt("status"), rs.getInt("retry_count"), rs.getInt("max_retry"));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record Row(Long id, String topic, String payload, int status, int retryCount, int maxRetry) {
    }
}
