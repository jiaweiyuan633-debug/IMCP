package cn.admin.scaffold.security;

import cn.admin.scaffold.common.PathNormalizer;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.config.SecurityProperties;
import cn.admin.scaffold.module.system.ApiPermRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 资源级权限过滤器：在 JWT 认证之后、授权决策之前执行。
 *
 * <p>对命中的 URL（method+path 在 {@link ApiPermRegistry} 中有映射）校验当前用户是否持有对应权限编码；
 * 无匹配的 URL 放行（保持仅认证约束）。匹配前按 {@link PathNormalizer} 归一化请求路径（去矩阵参数/
 * URL 解码/折叠重复斜杠/去尾斜杠//api/v1 前缀重写），与规则口径统一，防止路径变体绕过规则。
 *
 * <p>可观测性：已认证请求命不中任何规则（且非个人自助白名单）时以限流频率输出 warn（method+path），
 * 便于发现规则漏配；可选开启严格模式（{@code SecurityProperties.apiPermStrict}，默认 false）——开启后
 * 这类「仅认证」端点一律 403，把漏配从「可访问」变成「显式拒绝」。个人自助端点（改密/登出/刷新/
 * me/profile/totp 等）有意不登记资源权限，恒定豁免。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPermAuthorizationFilter extends OncePerRequestFilter {

    /** 同一 method:path 未命中告警的抑制窗口（毫秒）：避免高 QPS 下刷爆日志。 */
    private static final long WARN_SUPPRESS_MILLIS = 5 * 60_000L;
    /** 告警去重表上限：超出即整体清空重计（端点数量有界，防止误配置下无限增长）。 */
    private static final int WARN_MAP_CAP = 4096;

    private final ApiPermRegistry apiPermRegistry;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /** method:path → 最近一次 warn 时间戳（并发限流）。 */
    private final ConcurrentHashMap<String, Long> lastWarnAt = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawPath = request.getRequestURI();
        String required = apiPermRegistry.resolve(request.getMethod(), rawPath);
        if (required == null) {
            handleUnmatched(request, response, filterChain);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        boolean granted = authenticated && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> required.equals(grantedAuthority.getAuthority()));
        if (!granted) {
            writeError(response,
                    authenticated ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED,
                    authenticated ? ResultCode.FORBIDDEN : ResultCode.UNAUTHORIZED,
                    null);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void handleUnmatched(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        String normalizedPath = PathNormalizer.normalize(request.getRequestURI());
        boolean underApi = normalizedPath != null && normalizedPath.startsWith("/api/");
        String endpointKey = request.getMethod() + ":" + normalizedPath;
        boolean selfService = normalizedPath != null
                && SecurityEndpointAllowlists.AUTH_SELF_SERVICE.contains(endpointKey);
        if (authenticated && underApi && !selfService) {
            warnThrottled(endpointKey);
            if (securityProperties.isApiPermStrict()) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN,
                        "该接口未登记资源权限规则且已开启严格模式，已拒绝访问（请补齐 sys_api_perm 规则或关闭严格模式）");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 未命中规则告警：同一 method:path 每窗口仅记一次。 */
    private void warnThrottled(String endpointKey) {
        long now = System.currentTimeMillis();
        Long last = lastWarnAt.get(endpointKey);
        if (last != null && now - last < WARN_SUPPRESS_MILLIS) {
            return;
        }
        if (lastWarnAt.size() > WARN_MAP_CAP) {
            lastWarnAt.clear();
        }
        lastWarnAt.put(endpointKey, now);
        log.warn("API 资源权限规则未命中，仅认证放行：{}（若属受管接口请登记 sys_api_perm 规则）", endpointKey);
    }

    private void writeError(HttpServletResponse response, int status, ResultCode resultCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (message == null) {
            objectMapper.writeValue(response.getWriter(), Result.error(resultCode));
        } else {
            objectMapper.writeValue(response.getWriter(), Result.error(resultCode.getCode(), message));
        }
    }
}
