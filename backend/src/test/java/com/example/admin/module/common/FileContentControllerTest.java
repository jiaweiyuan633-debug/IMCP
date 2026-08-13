package com.example.admin.module.common;

import com.example.admin.common.FileAccessService;
import com.example.admin.module.system.entity.SysFileDO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileContentControllerTest {

    private final FileStorageManager fileStorageManager = mock(FileStorageManager.class);
    private final FileAccessService fileAccessService = new FileAccessService("test-secret");
    private final FileContentController controller = new FileContentController(fileStorageManager, fileAccessService);

    @Test
    void fileContentUsesPrivateCacheControlMatchingTokenTtl() {
        // R3-1.2：文件受访问令牌保护（URL 携带绑定用户的 token），是私有资源，
        // 禁止公共代理/CDN 缓存；max-age 与令牌有效期对齐，缓存命中时令牌必然仍有效
        SysFileDO file = new SysFileDO();
        file.setId(1L);
        file.setSize(100L);
        file.setOriginalName("test.png");
        file.setContentType("image/png");
        file.setSha256("abc");
        when(fileStorageManager.getById(1L)).thenReturn(file);
        when(fileStorageManager.open(file)).thenReturn(new ByteArrayInputStream(new byte[100]));

        ResponseEntity<InputStreamResource> response = controller.content(1L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cacheControl = response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL);
        assertThat(cacheControl).contains("private").doesNotContain("public");
        assertThat(cacheControl).contains("max-age=" + fileAccessService.getTokenTtlSeconds());
    }
}
