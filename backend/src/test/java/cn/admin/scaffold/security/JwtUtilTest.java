package cn.admin.scaffold.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtUtilTest {

    @Test
    void createAndParseAccessToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-that-is-long-enough-for-hs256-signing");
        properties.setAccessTokenExpireMinutes(120);
        JwtUtil jwtUtil = new JwtUtil(properties);

        String token = jwtUtil.createAccessToken(
                "jti-001", 1L, "admin", 2L, List.of("admin"), List.of("system:user:list"));

        Claims claims = jwtUtil.parse(token);
        assertEquals("1", claims.getSubject());
        assertEquals("jti-001", claims.getId());
        assertEquals("admin", claims.get("username"));
        assertEquals(2L, ((Number) claims.get("tenantId")).longValue());
        assertEquals(List.of("admin"), claims.get("roles", List.class));

        // refresh token 同样携带租户声明，供 refresh/filter 查询前就位租户上下文
        String refreshToken = jwtUtil.createRefreshToken("rt-001", 1L, "admin", 2L);
        Claims refreshClaims = jwtUtil.parse(refreshToken);
        assertEquals(2L, ((Number) refreshClaims.get("tenantId")).longValue());
    }
}

