package com.example.admin.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class FileAccessService {

    private static final long TOKEN_TTL_SECONDS = 3600L;

    private final String secret;

    public FileAccessService(@Value("${jwt.secret:admin-scaffold-jwt-secret-key-2026-change-me-in-production}") String secret) {
        this.secret = secret;
    }

    public String issue(String path) {
        long expires = System.currentTimeMillis() / 1000 + TOKEN_TTL_SECONDS;
        String payload = path + ":" + expires;
        return expires + "." + sign(payload);
    }

    public boolean verify(String path, String token) {
        if (path == null || token == null || !token.contains(".")) {
            return false;
        }
        try {
            String[] parts = token.split("\\.", 2);
            long expires = Long.parseLong(parts[0]);
            if (expires < System.currentTimeMillis() / 1000) {
                return false;
            }
            String expected = sign(path + ":" + expires);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("File token signing failed", exception);
        }
    }
}
