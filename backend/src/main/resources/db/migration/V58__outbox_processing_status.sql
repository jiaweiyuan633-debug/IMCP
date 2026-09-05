-- R4-1.30：发件箱投递增加「投递中」中间态（status=4）
-- 仅调整列注释（不改存储结构与已有数据），语义由 OutboxDispatcher 原子抢占使用：
-- 两路投递（事务提交即时投递 + 定时清扫）与多副本清扫对同一行并发调用 handler 前，
-- 先以条件更新 PENDING/FAILED → PROCESSING 抢占，抢占成功者才执行，防止重复副作用。
ALTER TABLE sys_outbox
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
        COMMENT '0待投递 1投递成功 2失败待重试 3失败终态(达上限) 4投递中(已被抢占，防重复投递)';
