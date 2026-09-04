package cn.admin.scaffold.common;

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

    @Test
    void publicTokenAcceptedByAnonymousAndLoggedInUser() {
        // R2-1.4：匿名上传/系统任务签发公开资源令牌（boundUserId 为空）。
        // 登录用户打开同一分享链接不应被拒——否则「匿名可看、登录反被拒」，破坏 URL 分享语义。
        String token = fileAccessService.issue("/files/123", null);
        assertTrue(token.split("\\.", 3)[1].isEmpty());
        assertTrue(fileAccessService.verify("/files/123", token, null));
        assertTrue(fileAccessService.verify("/files/123", token, 9L));
    }

    @Test
    void tamperedBoundUserStillRejected() {
        // 攻击者把绑定用户段清空试图伪装成公开令牌 → 绑定段被签名整体覆盖，签名必不匹配
        String token = fileAccessService.issue("/files/123", 1L);
        String[] parts = token.split("\\.", 3);
        String forgedPublic = parts[0] + ".." + parts[2];
        assertFalse(fileAccessService.verify("/files/123", forgedPublic, null));
        assertFalse(fileAccessService.verify("/files/123", forgedPublic, 1L));
    }

    @Test
    void malformedTokenRejected() {
        assertFalse(fileAccessService.verify("/files/123", "abc", 1L));
        assertFalse(fileAccessService.verify("/files/123", "1.2", 1L));
        assertFalse(fileAccessService.verify("/files/123", "not-a-number.1.abc", 1L));
    }
}
