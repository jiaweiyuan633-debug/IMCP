package com.example.admin.config;

import com.example.admin.security.ApiPermAuthorizationFilter;
import com.example.admin.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * CORS 兜底策略单测（R4-1.45 批次18）。
 *
 * <p>{@code @Value("${app.cors.allowed-origin-patterns:*}")} 的兜底 `*` 是最后防线：仅当
 * application.yml 中该属性段被整体移除时才会命中，此时凭据跨域意外全开；改为空串兜底后
 * 与 application-prod.yml「缺省拒绝」对齐，未配置即拒绝全部跨域来源。同时验证空段（a,,b）
 * 被过滤，不产生空 pattern 参与匹配。
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig(
            mock(JwtAuthenticationFilter.class),
            mock(ApiPermAuthorizationFilter.class),
            new ObjectMapper());

    @BeforeEach
    void setUp() {
        // 默认注入兜底值，保持与生产行为一致
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatterns", "");
    }

    private List<String> patternsFor(String configured) {
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatterns", configured);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());
        return configuration.getAllowedOriginPatterns();
    }

    @Test
    void emptyDefaultDeniesAllCrossOrigin() {
        // 未配置（缺省段被移除）→ 空 pattern → 无来源匹配，凭据跨域拒绝
        assertThat(patternsFor("")).isEmpty();
    }

    @Test
    void explicitStarStillAllowsAllPatterns() {
        assertThat(patternsFor("*")).containsExactly("*");
    }

    @Test
    void commaListFiltersBlankSegments() {
        // "a,,b" 的空段被过滤，不产生空 pattern
        assertThat(patternsFor(" https://a.example.com ,, https://b.example.com "))
                .containsExactly("https://a.example.com", "https://b.example.com");
    }
}
