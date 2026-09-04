package cn.admin.scaffold.config;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MCP Server 端点鉴权：/mcp 与 /mcp/message 对外暴露平台只读工具（含用户手机号/邮箱等敏感字段），
 * 不能匿名开放。校验 Authorization: Bearer &lt;app.mcp.auth-token&gt;（常量时间比较防时序侧信道）。
 * fail-closed：未配置令牌，或非 dev 环境使用开发默认值，一律拒绝连接。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String DEV_FALLBACK_TOKEN = "dev-mcp-token";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String authToken;
    private final boolean devProfile;
    private final ObjectMapper objectMapper;

    public McpAuthFilter(@Value("${app.mcp.auth-token:}") String authToken,
                         ObjectMapper objectMapper,
                         Environment environment) {
        this.authToken = authToken;
        this.objectMapper = objectMapper;
        this.devProfile = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !McpServerConfig.MCP_SSE_ENDPOINT.equals(uri)
                && !McpServerConfig.MCP_MESSAGE_ENDPOINT.equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (tokenUsable() && matches(request.getHeader("Authorization"))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.UNAUTHORIZED));
    }

    private boolean tokenUsable() {
        return StringUtils.hasText(authToken) && (devProfile || !DEV_FALLBACK_TOKEN.equals(authToken));
    }

    private boolean matches(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String presented = authorizationHeader.substring(BEARER_PREFIX.length());
        byte[] expected = authToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = presented.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
