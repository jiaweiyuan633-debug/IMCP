package cn.admin.scaffold.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 历史/开发默认密钥，仅允许在 dev profile 下使用 */
    private static final String DEV_FALLBACK_SECRET = "dev-only-jwt-secret-please-override-0123456789abcdef";
    private static final String LEGACY_DEFAULT_SECRET = "admin-scaffold-jwt-secret-key-2026-change-me-in-production";
    private static final int MIN_SECRET_LENGTH = 32;

    /** 由 @ConfigurationProperties 绑定注入（缺失时留空串，validateSecret 按"未配置" fail-fast）。 */
    private String secret = "";
    private long accessTokenExpireMinutes = 120;
    private long refreshTokenExpireDays = 7;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void validateSecret() {
        boolean isDev = environment != null && environment.acceptsProfiles(Profiles.of("dev"));
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT_SECRET 未配置，请通过环境变量注入");
        }
        boolean isKnownDefault = Objects.equals(secret, DEV_FALLBACK_SECRET)
                || Objects.equals(secret, LEGACY_DEFAULT_SECRET)
                || secret.contains("change-me");
        if (isKnownDefault && !isDev) {
            throw new IllegalStateException("生产环境禁止使用默认/占位 JWT 密钥，请配置独立的 JWT_SECRET");
        }
        if (isDev && isKnownDefault) {
            log.warn("当前使用开发默认 JWT 密钥，仅限本地开发；生产必须通过环境变量 JWT_SECRET 注入独立密钥");
        }
        if (secret.length() < MIN_SECRET_LENGTH && !isDev) {
            throw new IllegalStateException("JWT_SECRET 长度不足 " + MIN_SECRET_LENGTH + " 位，请配置更强的密钥");
        }
    }
}

