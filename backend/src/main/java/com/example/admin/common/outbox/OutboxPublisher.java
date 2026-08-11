package com.example.admin.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 事务发件箱写入器（Transactional Outbox）。
 *
 * <p>用法：在业务事务内调用 {@link #publish(String, String)} 写入发件箱行，
 * 事务提交后通过 {@link OutboxInsertedEvent}（AFTER_COMMIT）触发 {@link OutboxDispatcher#dispatch}
 * 立即投递；即使提交后进程崩溃，后台轮询也会兜底补投，达成"业务状态与外部副作用至少一次"一致。
 *
 * <p>可靠性要点：事件在事务内发布、提交后投递——事务回滚时事件不发布、行也被回滚，
 * 不会出现"业务未提交但外发已执行"的幻影副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    static final int DEFAULT_MAX_RETRY = 5;

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxDispatcher dispatcher;

    /**
     * 在事务内写入一条待投递事件。调用方须处于事务上下文（REQUIRED 传播可合并到外层事务）。
     *
     * @param topic   事件主题，需存在对应 {@link OutboxHandler}
     * @param payload 投递负载（JSON 字符串）
     * @return 发件箱行主键
     */
    @Transactional
    public Long publish(String topic, String payload) {
        org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_outbox (topic, payload, status, max_retry) VALUES (?, ?, 0, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, topic);
            ps.setString(2, payload);
            ps.setInt(3, DEFAULT_MAX_RETRY);
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (id != null) {
            // 事务提交后由 onCommitted 触发投递
            eventPublisher.publishEvent(new OutboxInsertedEvent(id));
        }
        return id;
    }

    /**
     * 事务提交后立即投递。AFTER_COMMIT 保证仅在业务事务成功提交后外发；
     * fallbackExecution 兜底"无活动事务"的调用（如测试直连），避免事件被丢弃。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommitted(OutboxInsertedEvent event) {
        dispatcher.dispatch(event.outboxId());
    }
}
