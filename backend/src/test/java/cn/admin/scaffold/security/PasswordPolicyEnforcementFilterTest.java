package cn.admin.scaffold.security;

import cn.admin.scaffold.config.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 口令生命周期服务端强制拦截单测：受限账号除白名单端点外一律 403，
 * 白名单（改密/登出/刷新/自身信息）放行；策略关闭（dev/test）整体放行。
 */
class PasswordPolicyEnforcementFilterTest {

    private final SecurityProperties securityProperties = new SecurityProperties();
    private PasswordPolicyEnforcementFilter filter;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        // 默认 forcePasswordChange=true（prod 语义）；受影响测试用例按需关闭
        filter = new PasswordPolicyEnforcementFilter(securityProperties, new ObjectMapper());
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsRestricted(boolean restricted) {
        LoginUser loginUser = LoginUser.builder()
                .userId(1L)
                .passwordChangeRequired(restricted)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        loginUser, null, loginUser.getAuthorities()));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void restrictedUserBlockedOnBusinessEndpoint() throws Exception {
        loginAsRestricted(true);

        filter.doFilter(request("GET", "/api/system/user/page"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restrictedUserAllowedOnPasswordChangeEndpoint() throws Exception {
        loginAsRestricted(true);

        filter.doFilter(request("PUT", "/api/auth/password"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restrictedUserAllowedOnMeAndLogoutEndpoints() throws Exception {
        loginAsRestricted(true);

        filter.doFilter(request("GET", "/api/auth/me"), response, chain);
        filter.doFilter(request("POST", "/api/auth/logout"), response, chain);

        verify(chain, org.mockito.Mockito.times(2))
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unrestrictedUserPassesBusinessEndpoint() throws Exception {
        loginAsRestricted(false);

        filter.doFilter(request("GET", "/api/system/user/page"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anonymousRequestNeverBlocked() throws Exception {
        // 无认证上下文（登录/验证码/公开资源）：策略不适用
        filter.doFilter(request("POST", "/api/auth/login"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void policyDisabledPassesEverything() throws Exception {
        // dev/test：forcePasswordChange=false 整体放行（保持 admin/admin123 开发/CI 体验）
        securityProperties.setForcePasswordChange(false);
        loginAsRestricted(true);

        filter.doFilter(request("GET", "/api/system/user/page"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
