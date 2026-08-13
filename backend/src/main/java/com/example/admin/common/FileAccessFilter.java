package com.example.admin.common;

import com.example.admin.security.SecurityUtils;
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
        String uri = request.getRequestURI();
        if (!uri.startsWith("/uploads/") && !uri.matches("/files/\\d+")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = request.getParameter("token");
        // 已登录请求强制校验令牌绑定用户（防跨用户复用）；匿名预览请求凭签名与有效期访问
        if (fileAccessService.verify(uri, token, SecurityUtils.tryGetUserId())) {
            // R3-1.2：静态资源路径（/uploads/**）不带缓存头，前端列表/预览重复全量下载。
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
