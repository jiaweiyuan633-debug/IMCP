package cn.admin.scaffold.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    private final JwtProperties properties;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
    }

    public String generateJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 签发访问令牌。token 携带 tenantId，认证链（JwtAuthenticationFilter / refresh）
     * 在首个数据库查询前先据其就位租户上下文，避免租户拦截器注入默认 tenant_id=1 使
     * 非租户 1 用户的角色/权限/用户查询全部落空。
     */
    public String createAccessToken(String jti, Long userId, String username, Long tenantId,
                                    List<String> roles, List<String> perms) {
        Instant now = Instant.now();
        Date nowDate = Date.from(now);
        Date expiration = Date.from(now.plusMillis(properties.getAccessTokenExpireMinutes() * 60_000L));
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("username", username)
                .claim("roles", roles)
                .claim("perms", perms)
                .issuedAt(nowDate)
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    public String createRefreshToken(String jti, Long userId, String username, Long tenantId) {
        Instant now = Instant.now();
        Date nowDate = Date.from(now);
        Date expiration = Date.from(now.plusMillis(properties.getRefreshTokenExpireDays() * 24 * 60 * 60_000L));
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("username", username)
                .issuedAt(nowDate)
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}

