-- 事务发件箱（批次1 可靠性纵深）：跨事务可靠事件投递
-- 业务事务内 INSERT 本表，事务提交后由 OutboxDispatcher 投递，避免"业务已提交但外发失败静默丢失"。
-- 投递失败按指数退避重试，超过 max_retry 进入终态失败（status=3），供补偿/人工排查。
CREATE TABLE sys_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic VARCHAR(100) NOT NULL COMMENT '事件主题，路由到对应 OutboxHandler',
    payload JSON NOT NULL COMMENT '投递负载（JSON）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待投递 1投递成功 2失败待重试 3失败终态(达上限)',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_at DATETIME NULL COMMENT '下次重试时间（指数退避），空表示可立即投递',
    last_error VARCHAR(1000) NULL COMMENT '最近一次失败原因',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_outbox_poll (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务发件箱：可靠事件投递';
