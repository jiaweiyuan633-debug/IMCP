package com.example.admin.security;

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
                "jti-001", 1L, "admin", List.of("admin"), List.of("system:user:list"));

        Claims claims = jwtUtil.parse(token);
        assertEquals("1", claims.getSubject());
        assertEquals("jti-001", claims.getId());
        assertEquals("admin", claims.get("username"));
        assertEquals(List.of("admin"), claims.get("roles", List.class));
    }
}

