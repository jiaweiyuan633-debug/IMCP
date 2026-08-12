package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.BusinessMetrics;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.auth.dto.LoginRequest;
import com.example.admin.module.auth.dto.RefreshRequest;
import com.example.admin.module.system.entity.SysConfigDO;
import com.example.admin.module.system.entity.SysLoginLogDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.mapper.SysLoginLogMapper;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.security.JwtUtil;
import com.example.admin.security.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R1-1.7 回归测试：登录/刷新链路必须跨租户定位用户，且 token 携带租户上下文。
 *
 * <p>根因：登录、刷新、JWT 过滤器查询租户表时租户上下文尚未就位，租户拦截器注入默认
 * tenant_id=1，非租户 1 用户无法登录/刷新。修复：login 改用豁免租户拦截器的 selectByUsername
 * 跨租户查询；JWT 携带 tenantId，刷新/鉴权链路在首个查询前先就位租户上下文。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysLoginLogMapper loginLogMapper;
    @Mock
    private SysConfigMapper configMapper;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TotpService totpService;
    @Mock
    private BusinessMetrics businessMetrics;
    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthService authService;

    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        // loginConfig() 构造 LambdaQueryWrapper<SysConfigDO>.eq(...) 需解析列名
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysConfigDO.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysLoginLogDO.class);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> stubRedis() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return valueOps;
    }

    private SysUserDO tenantUser(Long id, Long tenantId, String username) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword("hashed");
        user.setStatus(1);
        return user;
    }

    private LoginRequest loginRequest(String username, String password, Long tenantId) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setTenantId(tenantId);
        return request;
    }

    @Test
    void loginResolvesUserAcrossTenants() {
        stubRedis();
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(configMapper.selectOne(any())).thenReturn(null); // 验证码关闭
        SysUserDO user = tenantUser(10L, 2L, "zhangsan");
        when(userMapper.selectByUsername("zhangsan", null)).thenReturn(List.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateJti()).thenReturn("jti-1");
        when(roleMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of());
        when(menuMapper.selectPermsByUserId(10L)).thenReturn(List.of());
        when(menuMapper.selectMenusByUserId(10L)).thenReturn(List.of());

        authService.login(loginRequest("zhangsan", "secret", null), httpRequest);

        // 非租户 1 用户可登录，租户上下文按用户所属租户就位
        assertThat(TenantContext.getTenantId()).isEqualTo(2L);
        // 签发的 access/refresh token 均携带租户声明
        verify(jwtUtil).createAccessToken("jti-1", 10L, "zhangsan", 2L, List.of(), List.of());
        verify(jwtUtil).createRefreshToken("jti-1", 10L, "zhangsan", 2L);
    }

    @Test
    void loginUsesExplicitTenantWhenProvided() {
        stubRedis();
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(configMapper.selectOne(any())).thenReturn(null);
        SysUserDO user = tenantUser(20L, 2L, "zhangsan");
        // 指定租户后精确按 (tenant_id, username) 定位，即使跨租户同名也不会多行
        when(userMapper.selectByUsername("zhangsan", 2L)).thenReturn(List.of(user));
        when(passwordEncoder.matches("secret", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateJti()).thenReturn("jti-1");
        when(roleMapper.selectRoleCodesByUserId(20L)).thenReturn(List.of());
        when(menuMapper.selectPermsByUserId(20L)).thenReturn(List.of());
        when(menuMapper.selectMenusByUserId(20L)).thenReturn(List.of());

        authService.login(loginRequest("zhangsan", "secret", 2L), httpRequest);

        assertThat(TenantContext.getTenantId()).isEqualTo(2L);
    }

    @Test
    void loginRejectsAmbiguousUsernameWithoutTenant() {
        stubRedis();
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(configMapper.selectOne(any())).thenReturn(null);
        // 跨租户同名且未指定租户：无法唯一定位，抛出业务异常而非 500
        when(userMapper.selectByUsername("zhangsan", null))
                .thenReturn(List.of(tenantUser(1L, 1L, "zhangsan"), tenantUser(2L, 2L, "zhangsan")));

        assertThatThrownBy(() -> authService.login(loginRequest("zhangsan", "secret", null), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.USERNAME_AMBIGUOUS.getMessage());
        // 该次失败计入登录锁定，防恶意探测
        verify(loginLogMapper).insert(any(SysLoginLogDO.class));
    }

    @Test
    void refreshSetsTenantContextBeforeUserQuery() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("rt-1");
        when(claims.getSubject()).thenReturn("10");
        when(claims.get("tenantId")).thenReturn(2L);
        when(jwtUtil.parse("refresh-token")).thenReturn(claims);
        when(tokenService.hasRefreshToken("rt-1")).thenReturn(true);
        AtomicLong tenantAtUserQuery = new AtomicLong();
        SysUserDO user = tenantUser(10L, 2L, "zhangsan");
        when(userMapper.selectById(10L)).thenAnswer(invocation -> {
            tenantAtUserQuery.set(TenantContext.getTenantId());
            return user;
        });
        when(roleMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of());
        when(menuMapper.selectPermsByUserId(10L)).thenReturn(List.of());
        when(menuMapper.selectMenusByUserId(10L)).thenReturn(List.of());
        when(jwtUtil.generateJti()).thenReturn("jti-2");

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");
        authService.refresh(request);

        // selectById 执行时租户上下文已按 token 声明就位（而非默认 1）
        assertThat(tenantAtUserQuery.get()).isEqualTo(2L);
        verify(jwtUtil).createAccessToken("jti-2", 10L, "zhangsan", 2L, List.of(), List.of());
    }
}
