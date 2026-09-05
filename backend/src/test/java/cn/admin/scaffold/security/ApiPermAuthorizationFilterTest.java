package cn.admin.scaffold.security;

import cn.admin.scaffold.config.SecurityProperties;
import cn.admin.scaffold.module.system.ApiPermRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiPermAuthorizationFilterTest {

    private ApiPermRegistry registry;
    private ObjectMapper objectMapper;
    private SecurityProperties securityProperties;
    private ApiPermAuthorizationFilter filter;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        registry = mock(ApiPermRegistry.class);
        objectMapper = new ObjectMapper();
        securityProperties = new SecurityProperties();
        filter = new ApiPermAuthorizationFilter(registry, securityProperties, objectMapper);
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(boolean authenticated, String... authorities) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user", null,
                authenticated
                        ? List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
                        : List.of());
        if (authenticated) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void passesThroughWhenNoRuleMatches() throws Exception {
        when(registry.resolve(anyString(), anyString())).thenReturn(null);

        filter.doFilter(request("GET", "/api/system/user"), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesThroughWhenAuthenticatedAndHasPerm() throws Exception {
        when(registry.resolve(anyString(), anyString())).thenReturn("system:user:add");
        login(true, "system:user:add");

        filter.doFilter(request("POST", "/api/system/user/1"), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsWith403WhenAuthenticatedButLacksPerm() throws Exception {
        when(registry.resolve(anyString(), anyString())).thenReturn("system:user:add");
        login(true, "system:user:list");

        filter.doFilter(request("POST", "/api/system/user/1"), response, chain);

        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void rejectsWith401WhenNotAuthenticated() throws Exception {
        when(registry.resolve(anyString(), anyString())).thenReturn("system:user:add");
        login(false);

        filter.doFilter(request("POST", "/api/system/user/1"), response, chain);

        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsWith403WhenPrincipalNotUserDetails() throws Exception {
        when(registry.resolve(anyString(), anyString())).thenReturn("system:user:add");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain", null, List.of()));

        filter.doFilter(request("POST", "/api/system/user/1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void strictModeRejectsUnmatchedEndpointWhenAuthenticated() throws Exception {
        // 严格模式（opt-in）：已认证请求命不中任何规则即 403，把"规则漏配"从仅认证放行变为显式拒绝
        when(registry.resolve(anyString(), anyString())).thenReturn(null);
        securityProperties.setApiPermStrict(true);
        login(true);

        filter.doFilter(request("GET", "/api/system/menu/tree"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void strictModeKeepsAuthSelfServiceEndpointsReachable() throws Exception {
        // 个人自助端点有意不登记资源权限，严格模式下恒定豁免，不被误杀
        when(registry.resolve(anyString(), anyString())).thenReturn(null);
        securityProperties.setApiPermStrict(true);
        login(true);

        filter.doFilter(request("GET", "/api/auth/me"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
