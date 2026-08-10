package com.example.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 锁定 application-prod.yml 的多文档结构（P2-15）：
 * - 基础文档默认单实例 Redis（host/port，无 sentinel 段）
 * - 哨兵覆盖文档以 spring.config.activate.on-property 条件激活，
 *   注入 REDIS_SENTINEL_MASTER 时才切换主从哨兵拓扑。
 * 仅校验 YAML 结构与键名（防手误），激活语义由 Spring Boot config-data 机制保证。
 */
class ProdConfigStructureTest {

    private List<PropertySource<?>> loadProdDocs() throws Exception {
        return new YamlPropertySourceLoader().load("prod",
                new ClassPathResource("application-prod.yml"));
    }

    @Test
    void prodDefinesGracefulShutdown() throws Exception {
        PropertySource<?> base = loadProdDocs().get(0);
        assertEquals("graceful", base.getProperty("server.shutdown"));
        assertEquals("30s", base.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
    }

    @Test
    void baseDocDefaultsToStandaloneRedis() throws Exception {
        PropertySource<?> base = loadProdDocs().get(0);
        assertEquals("${REDIS_HOST}", base.getProperty("spring.data.redis.host"));
        assertNull(base.getProperty("spring.data.redis.sentinel.master"));
    }

    @Test
    void sentinelOverlayActivatesOnMasterProperty() throws Exception {
        PropertySource<?> overlay = loadProdDocs().stream()
                .filter(ps -> ps.getProperty("spring.config.activate.on-property") != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少哨兵覆盖文档（spring.config.activate.on-property）"));
        assertEquals("REDIS_SENTINEL_MASTER", overlay.getProperty("spring.config.activate.on-property"));
        assertEquals("${REDIS_SENTINEL_MASTER}", overlay.getProperty("spring.data.redis.sentinel.master"));
        assertNotNull(overlay.getProperty("spring.data.redis.sentinel.nodes"));
        // 覆盖文档不回落单实例 host/port：一旦激活由 sentinel 配置接管连接工厂
        assertNull(overlay.getProperty("spring.data.redis.host"));
    }
}
