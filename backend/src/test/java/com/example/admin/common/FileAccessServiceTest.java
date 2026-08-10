package com.example.admin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAccessServiceTest {

    private final FileAccessService fileAccessService = new FileAccessService("test-secret");

    @Test
    void issueAndVerifyTokenForOwner() {
        String token = fileAccessService.issue("/uploads/test.png", 1L);
        assertTrue(fileAccessService.verify("/uploads/test.png", token, 1L));
        assertFalse(fileAccessService.verify("/uploads/other.png", token, 1L));
        assertFalse(fileAccessService.verify("/uploads/test.png", "tampered", 1L));
    }

    @Test
    void rejectsOtherUserReusingToken() {
        String token = fileAccessService.issue("/files/123", 1L);
        // 已登录的他人不得复用本人令牌
        assertFalse(fileAccessService.verify("/files/123", token, 2L));
    }

    @Test
    void allowsAnonymousPreviewBySignature() {
        String token = fileAccessService.issue("/files/123", 1L);
        // 匿名预览（img 等浏览器无法携带登录态）凭签名与有效期访问，保持 URL 分享语义
        assertTrue(fileAccessService.verify("/files/123", token, null));
    }

    @Test
    void rejectsExpiredToken() {
        long expires = System.currentTimeMillis() / 1000 - 10;
        String expired = expires + ".1.invalid-signature";
        assertFalse(fileAccessService.verify("/files/123", expired, 1L));
    }
}
