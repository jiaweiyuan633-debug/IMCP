package com.example.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 可靠性纵深（批次1）：
 * <ul>
 *   <li>{@code @EnableRetry} 开启 spring-retry 的 {@code @Retryable}（外部调用重试）；</li>
 *   <li>Resilience4j 由 resilience4j-spring-boot3 自动装配，提供 {@code @CircuitBreaker} 熔断，</li>
 *   <li>实例参数在 application.yml 的 {@code resilience4j.circuitbreaker.instances} 配置。</li>
 * </ul>
 */
@Configuration
@EnableRetry
public class ReliabilityConfig {
}
