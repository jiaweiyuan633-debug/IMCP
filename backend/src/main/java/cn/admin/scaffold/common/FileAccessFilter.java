package cn.admin.scaffold.common;

import cn.admin.scaffold.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
// 置于 Spring Security filter 链之后（默认 order -100），确保 JWT 已解析、可取得当前用户做令牌绑定校验
@Order(0)
@RequiredArgsConstructor
public class FileAccessFilter extends OncePerRequestFilter {

    private final FileAccessService fileAccessService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 用与 Spring MVC 路径解析一致的规范化 URI 匹配受保护路径。若以原始 getRequestURI()
        // 直接正则匹配，可被 ; 矩阵参数绕过：/files/5;x 不命中 /files/\d+ 漏检，而路由层剥掉 ;x
        // 落到 /files/5 并读文件，匿名即可跨租户下载任意文件（IDOR）。
        // 规范化口径与签发令牌的 CommonController 共用（FileAccessService.normalizePath 提升共用）。
        String uri = FileAccessService.normalizePath(request.getRequestURI());
        if (!uri.startsWith("/uploads/") && !uri.matches("/files/\\d+")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = request.getParameter("token");
        // 已登录请求强制校验令牌绑定用户（防跨用户复用）；匿名预览请求凭签名与有效期访问
        if (fileAccessService.verify(uri, token, SecurityUtils.tryGetUserId())) {
            // 静态资源路径（/uploads/**）不带缓存头，前端列表/预览重复全量下载。
            // 文件内容不可变（UUID 对象键），浏览器缓存 max-age 与令牌有效期对齐，
            // 缓存命中时令牌必然仍有效；/files/{id} 由 FileContentController 设置同值 private 头覆盖。
            response.setHeader(HttpHeaders.CACHE_CONTROL,
                    "private, max-age=" + fileAccessService.getTokenTtlSeconds());
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.FORBIDDEN));
    }
}
