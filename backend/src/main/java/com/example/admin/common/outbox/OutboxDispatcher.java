package com.example.admin.common.outbox;

import com.example.admin.common.LogMaskUtils;
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
 * 投递以条件更新原子抢占（PENDING/FAILED → PROCESSING）为界，保证两路投递与
 * 多副本清扫不会对同一行并发调用 handler。
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
    /** R4-1.30：投递中（已被某实例原子抢占，防止同一行被重复投递）。 */
    private static final int STATUS_PROCESSING = 4;
    /** 投递中状态滞留超时（分钟）：超过即视为抢占者崩溃/超长处理，允许清扫回收重新投递。 */
    private static final int PROCESSING_STALE_MINUTES = 5;

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
     * 投递单条发件箱行。先原子抢占（置 PROCESSING）再执行 handler：抢占成功才处理，
     * 成功置 1；失败记一次重试并退避（超限置 3）；未抢到直接跳过。
     *
     * <p>抢占是防重复投递的关键——事务提交即时投递与定时清扫两路、以及多副本清扫都可能
     * 并发处理同一行，若无中间态则 handler 会被重复调用产生重复副作用（webhook 重复外发等）。
     */
    public void dispatch(Long outboxId) {
        if (outboxId == null) {
            return;
        }
        // R4-1.30：条件更新抢占——仅当行仍可投递（待投递/待重试且已到重试时间）且未被他人
        // 领取时才置 PROCESSING；受影响行数为 0 说明已被抢占/已终态/未到重试时间，直接跳过。
        int claimed = jdbcTemplate.update(
                "UPDATE sys_outbox SET status = ?, updated_at = NOW() WHERE id = ?"
                        + " AND status IN (?, ?) AND (next_retry_at IS NULL OR next_retry_at <= NOW())",
                STATUS_PROCESSING, outboxId, STATUS_PENDING, STATUS_FAILED);
        if (claimed == 0) {
            return;
        }
        Row row = queryRow(outboxId);
        if (row == null) {
            // 抢占后行被并发删除的极端情况：无行可投递，残留 PROCESSING 状态无意义
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
            // R4-1.38：异常消息可能内嵌完整 webhook URL（含查询凭证），handler 层已自 sanitize，
            // 投递器层再统一兜底，防绕过脱敏进入日志与 last_error 列。
            error = LogMaskUtils.sanitize(truncate(t.getMessage()));
            log.warn("发件箱投递失败，topic={}，outboxId={}，retryCount={}，err={}", row.topic, outboxId, row.retryCount, error, t);
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

    /** 定时清扫待投递/待重试行（每 30s），并回收滞留过久的投递中行（抢占者崩溃/超长处理）。 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void pollExpired() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_outbox WHERE"
                        + " ((status IN (?, ?) AND (next_retry_at IS NULL OR next_retry_at <= NOW()))"
                        + "  OR (status = ? AND updated_at < NOW() - INTERVAL ? MINUTE))"
                        + " ORDER BY id LIMIT ?",
                Long.class, STATUS_PENDING, STATUS_FAILED, STATUS_PROCESSING, PROCESSING_STALE_MINUTES, POLL_BATCH_SIZE);
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
