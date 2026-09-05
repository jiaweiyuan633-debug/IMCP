package cn.admin.scaffold.security;

import cn.admin.scaffold.common.PathNormalizer;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.config.SecurityProperties;
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
 * 口令生命周期服务端强制拦截（服务端兜底，绕过前端的客户端同样被拦）。
 *
 * <p>前端只在登录/刷新/me 响应携带 mustChangePassword 时跳改密页——恶意/CLI/非浏览器客户端可绕过
 * 前端直接调用业务接口。本过滤器在 JWT 认证之后执行：当策略开关
 * （{@code SecurityProperties.forcePasswordChange}，prod 默认 true）开启且当前主体被判定
 * 「必须改密」（must_change_password=1 或口令过期，判定搭车 JwtAuthenticationFilter 的
 * {@link LoginUser#isPasswordChangeRequired()}，不额外查库）时，除白名单端点（改密/登出/刷新/
 * 自身信息）外一律返回 HTTP 403 + 既有业务码 FORBIDDEN，非浏览器/CLI 客户端同样被拦。
 *
 * <p>dev/test 环境开关关闭（保持 admin/admin123 直接登录的开发与 CI 体验），本过滤器整体放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordPolicyEnforcementFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 策略未开启（dev/test）：不拦截
        if (!securityProperties.isForcePasswordChange()) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean restrictedSubject = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUser loginUser
                && loginUser.isPasswordChangeRequired();
        if (!restrictedSubject) {
            // 匿名请求（登录/验证码/公开资源等）或非受限主体不在此策略范围
            filterChain.doFilter(request, response);
            return;
        }
        String normalizedPath = PathNormalizer.normalize(request.getRequestURI());
        boolean allowed = normalizedPath != null
                && SecurityEndpointAllowlists.PASSWORD_POLICY_EXEMPT
                .contains(request.getMethod() + ":" + normalizedPath);
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }
        writeForbidden(response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        // 业务码沿用既有 FORBIDDEN(403)，message 给出口令受限的具体原因供前端/CLI 提示
        objectMapper.writeValue(response.getWriter(),
                Result.error(ResultCode.FORBIDDEN.getCode(),
                        "账号需先修改默认/过期密码后方可继续操作，仅放行改密、登出、刷新与个人信息接口"));
    }
}
