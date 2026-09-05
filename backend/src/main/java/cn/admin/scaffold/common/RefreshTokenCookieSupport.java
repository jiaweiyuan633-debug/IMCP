package cn.admin.scaffold.common;

import cn.admin.scaffold.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * refresh token 的 httpOnly Cookie 读写。
 *
 * <p>把 refresh token 从前端 localStorage 迁移到 httpOnly Cookie：XSS 无法读取，降低长期
 * 会话被脚本窃取的风险。SameSite=Lax 兼容两种部署拓扑（localhost 跨端口 dev 直连与生产
 * 同源反代/Ingress）；Secure 仅在生产（https）开启，本地 http 下 Secure cookie 不会被保存。
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieSupport {

    private final SecurityProperties securityProperties;

    /** 写入/刷新 refresh token cookie。 */
    public void setRefreshCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        Cookie cookie = new Cookie(securityProperties.getRefreshCookieName(), refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(securityProperties.isRefreshCookieSecure());
        cookie.setPath(securityProperties.getRefreshCookiePath());
        cookie.setMaxAge((int) maxAgeSeconds);
        // SameSite 无法直接通过 Servlet Cookie API 设置，需手动加响应头
        response.addHeader("Set-Cookie", cookieHeader(cookie));
    }

    /** 清除 refresh token cookie（登出时调用）。 */
    public void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(securityProperties.getRefreshCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(securityProperties.isRefreshCookieSecure());
        cookie.setPath(securityProperties.getRefreshCookiePath());
        cookie.setMaxAge(0);
        response.addHeader("Set-Cookie", cookieHeader(cookie));
    }

    /** 读取 refresh token cookie（无则返回 null，由调用方回退 body）。 */
    public String readRefreshCookie(jakarta.servlet.http.HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (securityProperties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String cookieHeader(Cookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append('=').append(cookie.getValue())
                .append("; Path=").append(cookie.getPath())
                .append("; Max-Age=").append(cookie.getMaxAge())
                .append("; HttpOnly")
                .append("; SameSite=").append(securityProperties.getRefreshCookieSameSite());
        if (cookie.getSecure()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
