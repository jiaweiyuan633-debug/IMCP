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
 * token 结构：{expires}.{userId}.{HMAC-SHA256(path:expires:userId)}。
 * 已登录请求必须与令牌绑定用户一致，防止令牌跨用户复用；
 * 匿名请求（img 预览等浏览器无法携带登录态的场合）凭签名与有效期访问，保持 URL 分享语义。
 */
@Service
public class FileAccessService {

    private static final long TOKEN_TTL_SECONDS = 3600L;

    private final String secret;

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
            // 已登录请求必须与令牌绑定用户一致，防止他人复用令牌；
            // 匿名请求凭签名校验；系统签发的公开资源（未绑定用户）可匿名访问
            if (currentUserId != null && !String.valueOf(currentUserId).equals(boundUserId)) {
                return false;
            }
            if (currentUserId != null && !String.valueOf(currentUserId).equals(boundUserId)) {
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
