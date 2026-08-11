package com.example.admin.common.outbox;

import com.example.admin.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

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

        // 手动再次投递（模拟轮询到下次重试时间），重试次数继续累加、仍未超限
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

    private int queryStatus(Long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status FROM sys_outbox WHERE id = ?", id);
        return ((Number) row.get("status")).intValue();
    }
}
