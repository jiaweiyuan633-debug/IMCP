package com.example.admin.common.outbox;

import com.example.admin.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事务发件箱集成测试：验证「事务提交后投递→成功置终态 / 失败指数退避 / 未知主题终态」在真实 MySQL 上闭环。
 */
class OutboxDispatcherIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private OutboxDispatcher dispatcher;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 并发抢占测试的 handler 调用计数（handler 以 @Bean 暴露，测试经静态字段观测）。 */
    static final AtomicInteger CONCURRENT_CALLS = new AtomicInteger();

    @TestConfiguration
    static class ItHandlerConfig {
        @Bean
        OutboxHandler successHandler() {
            return new OutboxHandler() {
                @Override
                public String topic() {
                    return "it-event";
                }

                @Override
                public boolean handle(String payload) {
                    return true;
                }
            };
        }

        @Bean
        OutboxHandler failingHandler() {
            return new OutboxHandler() {
                @Override
                public String topic() {
                    return "it-fail";
                }

                @Override
                public boolean handle(String payload) {
                    throw new RuntimeException("boom");
                }
            };
        }

        @Bean
        OutboxHandler countingHandler() {
            return new OutboxHandler() {
                @Override
                public String topic() {
                    return "it-count";
                }

                @Override
                public boolean handle(String payload) {
                    CONCURRENT_CALLS.incrementAndGet();
                    return true;
                }
            };
        }
    }

    @Test
    void publishDeliversAndMarksSuccess() {
        Long id = publisher.publish("it-event", "{\"x\":1}");
        assertThat(queryStatus(id)).isEqualTo(1);
    }

    @Test
    void failingHandlerBacksOffThenDispatchedAgain() {
        Long id = publisher.publish("it-fail", "{\"x\":1}");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, next_retry_at FROM sys_outbox WHERE id = ?", id);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("retry_count")).intValue()).isEqualTo(1);
        assertThat(row.get("next_retry_at")).isNotNull();

        // R4-1.30：抢占前置——把下次重试时间拨到过去模拟轮询到期，否则 CAS 抢占会因
        // next_retry_at 未到期而拒绝（这正是防重复投递的边界），随后手动再投、重试次数继续累加
        jdbcTemplate.update("UPDATE sys_outbox SET next_retry_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id = ?", id);
        dispatcher.dispatch(id);
        Map<String, Object> again = jdbcTemplate.queryForMap(
                "SELECT status, retry_count FROM sys_outbox WHERE id = ?", id);
        assertThat(((Number) again.get("retry_count")).intValue()).isEqualTo(2);
        assertThat(((Number) again.get("status")).intValue()).isEqualTo(2);
    }

    @Test
    void unknownTopicMarksDead() {
        Long id = publisher.publish("it-unknown", "{\"x\":1}");
        assertThat(queryStatus(id)).isEqualTo(3);
    }

    @Test
    void concurrentDispatchClaimsRowOnce() throws Exception {
        // R4-1.30：模拟「即时投递与定时清扫/多副本清扫」两路并发争抢同一行——CAS 抢占
        // 保证 handler 只被调用一次，杜绝 webhook 等非幂等 handler 的重复副作用
        Long id = publisher.publish("it-count", "{\"x\":1}");
        jdbcTemplate.update("UPDATE sys_outbox SET status = 0, next_retry_at = NULL WHERE id = ?", id);
        CONCURRENT_CALLS.set(0);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    dispatcher.dispatch(id);
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(CONCURRENT_CALLS.get()).isEqualTo(1);
        assertThat(queryStatus(id)).isEqualTo(1);
    }

    private int queryStatus(Long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status FROM sys_outbox WHERE id = ?", id);
        return ((Number) row.get("status")).intValue();
    }
}
