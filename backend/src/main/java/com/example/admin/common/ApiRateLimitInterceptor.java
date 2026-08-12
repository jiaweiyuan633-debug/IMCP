package com.example.admin.common;

import com.example.admin.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "api:rate:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.api-rate-limit-per-minute:300}")
    private int limitPerMinute;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String identity = currentIdentity(request);
        String key = KEY_PREFIX + identity;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        if (count != null && count > limitPerMinute) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.TOO_MANY_REQUESTS));
            return false;
        }
        return true;
    }

    private String currentIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return "user:" + loginUser.getUserId();
        }
        // R2-1.3：匿名身份必须取 socket 层真实源地址，绝不可信任 X-Forwarded-For。
        // 该头由客户端完全可控：直接采信则攻击者可伪造任意前缀，无限绕过限流桶。
        // 真实客户端 IP 的透传由受信任反向代理（必须覆盖/重写 XFF）+ server.forward-headers-strategy
        // 负责，应用代码层不自行解析转发头，避免信任边界错位（默认 none = 反代场景全站共享桶，更严不更松）。
        return "ip:" + request.getRemoteAddr();
    }
}
