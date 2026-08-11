package com.example.admin.common.outbox;

/**
 * 发件箱行已写入（事务内发布）。由 {@link OutboxPublisher#publish} 发布，
 * {@link OutboxPublisher#onCommitted} 在事务提交后（AFTER_COMMIT）监听并立即投递，
 * 保证"事务未提交不投递、提交后才外发"的可靠性语义。
 *
 * @param outboxId 发件箱行主键
 */
public record OutboxInsertedEvent(Long outboxId) {
}
