package cn.admin.scaffold.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪 ID：读取客户端 X-Request-Id（回显便于链路追踪）或生成 UUID。
 *
 * <p>客户端值不可信：限定长度与可见字符白名单（[A-Za-z0-9._-]），超长/含非法字符回退 UUID，
 * 防止被写入日志/响应头造成日志注入或响应头拆分。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 客户端 X-Request-Id 最大长度（超出即回退 UUID）。 */
    private static final int MAX_CLIENT_REQUEST_ID_LENGTH = 64;

    private static final String CLIENT_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(CLIENT_HEADER));
        RequestIdHolder.set(requestId);
        response.setHeader(CLIENT_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdHolder.clear();
        }
    }

    /** 白名单校验 + 限长；非法值回退随机 UUID。 */
    private String sanitize(String clientValue) {
        if (clientValue == null || clientValue.isBlank()
                || clientValue.length() > MAX_CLIENT_REQUEST_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }
        String trimmed = clientValue.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                return UUID.randomUUID().toString();
            }
        }
        return trimmed;
    }
}

