package com.example.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 安全契约：application.yml 的默认 profile 必须为 prod。
 *
 * <p>未注入 SPRING_PROFILES_ACTIVE 时默认激活 prod（JWT/TOTP/MCP 密钥缺失即启动失败），
 * 杜绝部署误走 dev 明文兜底密钥上线。若被误改回 dev，本测试立即失败。
 */
class ProfileDefaultTest {

    @Test
    void defaultProfileIsProd() throws Exception {
        List<PropertySource<?>> docs = new YamlPropertySourceLoader().load("base",
                new ClassPathResource("application.yml"));
        PropertySource<?> base = docs.get(0);
        assertEquals("${SPRING_PROFILES_ACTIVE:prod}", base.getProperty("spring.profiles.active"));
    }
}
