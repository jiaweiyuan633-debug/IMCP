package com.example.admin.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class FileAccessFilterTest {

    private final FileAccessService fileAccessService = new FileAccessService("test-secret");
    private final FileAccessFilter filter = new FileAccessFilter(fileAccessService, new ObjectMapper());

    @Test
    void verifiedUploadResponseCarriesPrivateCacheControl() throws Exception {
        // R3-1.2：静态资源路径原本不带缓存头，前端列表/预览重复全量下载；
        // 校验通过后补 Cache-Control，max-age 与令牌有效期对齐
        String path = "/uploads/2026/01/01/abc.png";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.addParameter("token", fileAccessService.issue(path, null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, max-age=" + fileAccessService.getTokenTtlSeconds());
    }

    @Test
    void invalidTokenRejectedWithoutCacheControl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/uploads/2026/01/01/abc.png");
        request.addParameter("token", "forged-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isNull();
    }

    // ---------- R4-1.39：; 矩阵参数绕过 IDOR 修复 ----------

    /** /files/{id};x 经矩阵参数变体绕过旧正则（不命中 /files/\d+），规范化后必须校验 token，匿名无 token 拒绝。 */
    @Test
    void matrixParamVariantRejectsMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/files/5;x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    /** 带针对 /files/5 的有效 token：/files/5;x 规范化后校验通过，正常放行（缓存头生效）。 */
    @Test
    void matrixParamVariantWithValidTokenPasses() throws Exception {
        String path = "/files/5";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/files/5;x");
        request.addParameter("token", fileAccessService.issue(path, null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, max-age=" + fileAccessService.getTokenTtlSeconds());
    }
}
