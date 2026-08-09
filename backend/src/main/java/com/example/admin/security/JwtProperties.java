package com.example.admin.security;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessTokenExpireMinutes = 120;
    private long refreshTokenExpireDays = 7;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void validateSecret() {
        String defaultSecret = "admin-scaffold-jwt-secret-key-2026-change-me-in-production";
        if (Objects.equals(secret, defaultSecret)
                && environment != null
                && !environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("生产环境必须配置 JWT_SECRET，禁止使用默认密钥");
        }
    }
}

