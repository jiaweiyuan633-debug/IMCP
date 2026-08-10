package com.example.admin.security;

import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtUtil, tokenService, roleMapper, menuMapper, userMapper);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private Claims validClaims() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("username", String.class)).thenReturn("admin");
        return claims;
    }

    private SysUserDO activeUser() {
        SysUserDO user = new SysUserDO();
        user.setId(1L);
        user.setStatus(1);
        user.setTenantId(2L);
        user.setDeptId(3L);
        return user;
    }

    @Test
    void noTokenProceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void validTokenSetsAuthenticationAndTenant() throws Exception {
        Claims claims = validClaims();
        when(request.getHeader("Authorization")).thenReturn("Bearer abc");
        when(jwtUtil.parse("abc")).thenReturn(claims);
        when(tokenService.hasValidAccessToken("jti-1")).thenReturn(true);
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("admin"));
        when(tokenService.getCachedPermissions(1L)).thenReturn(null);
        when(menuMapper.selectPermsByUserId(1L)).thenReturn(List.of("system:user:list"));
        when(userMapper.selectById(1L)).thenReturn(activeUser());

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        LoginUser principal = assertInstanceOf(LoginUser.class, authentication.getPrincipal());
        assertEquals(1L, principal.getUserId());
        assertEquals(List.of("admin"), principal.getRoles());
        assertEquals(List.of("system:user:list"), principal.getPerms());
        assertEquals(2L, TenantContext.getTenantId());
        verify(tokenService).cachePermissions(1L, List.of("system:user:list"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledUserClearsContext() throws Exception {
        Claims claims = validClaims();
        when(request.getHeader("Authorization")).thenReturn("Bearer abc");
        when(jwtUtil.parse("abc")).thenReturn(claims);
        when(tokenService.hasValidAccessToken("jti-1")).thenReturn(true);
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of());
        when(tokenService.getCachedPermissions(1L)).thenReturn(List.of());
        SysUserDO user = activeUser();
        user.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(user);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenClearsContextAndProceeds() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad");
        when(jwtUtil.parse("bad")).thenThrow(new SignatureException("bad signature"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void blacklistedOrMissingAccessTokenSkipsAuthentication() throws Exception {
        Claims claims = validClaims();
        when(request.getHeader("Authorization")).thenReturn("Bearer abc");
        when(jwtUtil.parse("abc")).thenReturn(claims);
        when(tokenService.hasValidAccessToken("jti-1")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userMapper, never()).selectById(anyLong());
        verify(chain).doFilter(request, response);
    }

    @Test
    void alreadyAuthenticatedSkipsReAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer abc");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, List.of()));

        filter.doFilter(request, response, chain);

        verify(jwtUtil, never()).parse(anyString());
        verify(chain).doFilter(request, response);
    }
}
