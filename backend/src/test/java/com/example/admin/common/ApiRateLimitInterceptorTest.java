package com.example.admin.common;

import com.example.admin.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRateLimitInterceptorTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ApiRateLimitInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        interceptor = new ApiRateLimitInterceptor(redisTemplate, new ObjectMapper());
        // 单元测试无 Spring 上下文，@Value 字段不会注入（否则 limitPerMinute=0 拒绝一切请求）
        ReflectionTestUtils.setField(interceptor, "limitPerMinute", 300);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousUsesRemoteAddrAndIgnoresForgedXForwardedFor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        // 客户端可任意伪造的转发头，限流身份绝不得采信，否则攻击者无限换前缀绕过限流桶
        request.addHeader("X-Forwarded-For", "10.0.0.66, 10.0.0.67");
        when(valueOps.increment("api:rate:ip:1.2.3.4")).thenReturn(1L);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        // 桶键基于 socket 层真实源地址
        verify(valueOps).increment("api:rate:ip:1.2.3.4");
        // 伪造前缀永不进入任何限流桶
        verify(valueOps, never()).increment("api:rate:ip:10.0.0.66");
        verify(redisTemplate).expire("api:rate:ip:1.2.3.4", Duration.ofMinutes(1));
    }

    @Test
    void authenticatedUsesUserIdAsIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        LoginUser loginUser = LoginUser.builder().userId(42L).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
        when(valueOps.increment("api:rate:user:42")).thenReturn(1L);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        // 已登录用户按用户维度限流，与来源 IP 无关
        verify(valueOps).increment("api:rate:user:42");
        verify(valueOps, never()).increment("api:rate:ip:1.2.3.4");
    }

    @Test
    void overLimitResponds429() throws Exception {
        ReflectionTestUtils.setField(interceptor, "limitPerMinute", 2);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        when(valueOps.increment("api:rate:ip:1.2.3.4")).thenReturn(3L);

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        // MockHttpServletResponse 默认 ISO-8859-1，中文被替换为 ?，仅断言结构化错误码
        assertThat(response.getContentAsString()).contains("\"code\":429");
    }

    @Test
    void subsequentRequestsWithinWindowDoNotResetExpiry() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        // 窗口内后续请求（count>1）不再重置过期时间，保持 1 分钟滑动窗口
        when(valueOps.increment("api:rate:ip:1.2.3.4")).thenReturn(2L);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        verify(redisTemplate, never()).expire("api:rate:ip:1.2.3.4", Duration.ofMinutes(1));
    }
}
