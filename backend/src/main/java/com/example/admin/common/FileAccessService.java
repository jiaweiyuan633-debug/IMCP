package com.example.admin.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 文件访问令牌：签发时绑定文件路径与签发用户，消费时校验。
 * token 结构：{expires}.{userId}.{HMAC-SHA256(path:expires:userId)}，userId 为空表示公开资源令牌。
 * 绑定用户的令牌：已登录请求必须与绑定用户一致，防止令牌跨用户复用；
 * 匿名请求（img 预览等浏览器无法携带登录态的场合）凭签名与有效期访问，保持 URL 分享语义。
 * 系统签发的公开资源令牌（未绑定用户）：匿名与已登录请求一律凭签名访问——签发分享链接时
 * 发起方可能处于未登录态（如匿名上传），若登录用户访问被拒则出现「匿名可看、登录反被拒」，
 * 破坏分享语义。绑定段被签名整体覆盖，无法篡改伪装。
 */
@Service
public class FileAccessService {

    private static final long TOKEN_TTL_SECONDS = 3600L;

    private final String secret;

    /**
     * 令牌有效期（秒）：文件响应缓存 max-age 与其对齐，保证浏览器缓存命中时令牌必然仍有效，
     * 不会出现「已缓存过期令牌导致 403」。
     */
    public long getTokenTtlSeconds() {
        return TOKEN_TTL_SECONDS;
    }

    public FileAccessService(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("文件访问签名密钥未配置（读取 jwt.secret），请检查 JWT_SECRET 配置");
        }
        this.secret = secret;
    }

    public String issue(String path, Long userId) {
        long expires = System.currentTimeMillis() / 1000 + TOKEN_TTL_SECONDS;
        String boundUserId = userId == null ? "" : String.valueOf(userId);
        String payload = path + ":" + expires + ":" + boundUserId;
        return expires + "." + boundUserId + "." + sign(payload);
    }

    public boolean verify(String path, String token, Long currentUserId) {
        if (path == null || token == null) {
            return false;
        }
        String[] parts = token.split("\\.", 3);
        if (parts.length != 3) {
            return false;
        }
        try {
            long expires = Long.parseLong(parts[0]);
            if (expires < System.currentTimeMillis() / 1000) {
                return false;
            }
            String boundUserId = parts[1];
            // R2-1.4：绑定用户的令牌要求已登录请求与绑定用户一致，防止令牌跨用户复用；
            // 公开资源令牌（boundUserId 为空，如匿名上传时签发）任何请求（含已登录用户）均可访问，
            // 否则同一分享链接匿名可看、登录反被拒，破坏 URL 分享语义；
            // 匿名请求（img 预览等浏览器无法携带登录态的场合）一律凭签名与有效期校验。
            if (currentUserId != null && !boundUserId.isEmpty()
                    && !String.valueOf(currentUserId).equals(boundUserId)) {
                return false;
            }
            String expected = sign(path + ":" + expires + ":" + boundUserId);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("File token signing failed", exception);
        }
    }
}
