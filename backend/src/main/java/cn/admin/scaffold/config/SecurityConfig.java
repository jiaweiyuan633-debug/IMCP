package cn.admin.scaffold.config;

import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.security.ApiPermAuthorizationFilter;
import cn.admin.scaffold.security.JwtAuthenticationFilter;
import cn.admin.scaffold.security.JwtProperties;
import cn.admin.scaffold.security.PasswordPolicyEnforcementFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordPolicyEnforcementFilter passwordPolicyEnforcementFilter;
    private final ApiPermAuthorizationFilter apiPermAuthorizationFilter;
    private final ObjectMapper objectMapper;

    // 兜底为空串而非 "*"：未配置（含 application.yml 缺省段被移除）时拒绝全部跨域来源，
    // 与 application-prod.yml 的"缺省拒绝"对齐，避免凭据跨域意外全开。
    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          PasswordPolicyEnforcementFilter passwordPolicyEnforcementFilter,
                          ApiPermAuthorizationFilter apiPermAuthorizationFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.passwordPolicyEnforcementFilter = passwordPolicyEnforcementFilter;
        this.apiPermAuthorizationFilter = apiPermAuthorizationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // 全局安全响应头：显式声明 X-Content-Type-Options: nosniff（防浏览器 MIME 嗅探执行
                // 非预期类型响应）与 frame-options（防点击劫持）；其余默认头（HSTS 等）保持框架默认
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/captcha",
                                "/api/auth/login-config",
                                "/api/auth/oauth/providers",
                                "/api/auth/oauth/authorize-url",
                                "/api/auth/oauth/callback/**",
                                "/api/auth/oauth/ticket",
                                "/api/auth/oauth/bind",
                                "/api/oauth/token",
                                "/api/ai/callback/**",
                                // SSE 流：EventSource 无法携带 Authorization Header，
                                // 鉴权由一次性票据承担（ticket 登录态签发、60s 有效、单次消费），见 SseTicketService
                                "/api/system/notice/stream",
                                "/api/ai/tasks/*/stream",
                                "/api/report/screen/stream",
                                "/mcp",
                                "/mcp/message",
                                "/uploads/**",
                                "/files/**",
                                "/ws/**",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/favicon.ico",
                                "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 链路顺序：JWT 认证 → 口令生命周期强制（受限账号除白名单外 403）→ API 资源权限校验
                .addFilterAfter(passwordPolicyEnforcementFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(apiPermAuthorizationFilter, PasswordPolicyEnforcementFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // split 后过滤空段（配置含 "a,,b" 或整体为空串时不产生空 pattern 参与匹配）
        configuration.setAllowedOriginPatterns(
                Arrays.stream(allowedOriginPatterns.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList());
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response, int status, ResultCode resultCode) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(resultCode));
    }
}

