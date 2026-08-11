package com.example.admin.security;

import com.example.admin.common.Result;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.ApiPermRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 资源级权限过滤器：在 JWT 认证之后、授权决策之前执行。
 *
 * <p>对命中的 URL（method+path 在 {@link ApiPermRegistry} 中有映射）校验当前用户是否持有对应权限编码；
 * 无匹配的 URL 放行（保持仅认证约束）。白名单路径天然不受影响——它们不会出现在映射表中。
 * 注意：用户权限集合来自 LoginUser.getAuthorities()（角色 ROLE_ 前缀 + perm 原样），这里按 perm 精确匹配。
 */
@Component
@RequiredArgsConstructor
public class ApiPermAuthorizationFilter extends OncePerRequestFilter {

    private final ApiPermRegistry apiPermRegistry;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String required = apiPermRegistry.resolve(request.getMethod(), request.getRequestURI());
        if (required == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        boolean granted = authenticated && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> required.equals(grantedAuthority.getAuthority()));
        if (!granted) {
            writeError(response,
                    authenticated ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED,
                    authenticated ? ResultCode.FORBIDDEN : ResultCode.UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, ResultCode resultCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(resultCode));
    }
}
