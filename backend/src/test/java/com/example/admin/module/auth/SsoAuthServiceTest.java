package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.TenantContext;
import com.example.admin.module.auth.dto.SsoTokenRequest;
import com.example.admin.module.auth.entity.SysOauthClientDO;
import com.example.admin.module.auth.mapper.SysOauthClientMapper;
import com.example.admin.module.auth.vo.SsoTokenVo;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.security.JwtProperties;
import com.example.admin.security.JwtUtil;
import com.example.admin.security.SecurityUtils;
import com.example.admin.security.TokenService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ExtendWith(MockitoExtension.class)
class SsoAuthServiceTest {

    @Mock
    private SysOauthClientMapper oauthClientMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private TokenService tokenService;
    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private SsoAuthService ssoAuthService;

    @BeforeAll
    static void registerMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysOauthClientDO.class);
    }

    private SysOauthClientDO enabledClient(String redirectUri) {
        SysOauthClientDO client = new SysOauthClientDO();
        client.setClientId("app-1");
        client.setRedirectUri(redirectUri);
        client.setEnabled(1);
        return client;
    }

    @Test
    void authorizeRejectsPrefixBypassRedirect() {
        when(oauthClientMapper.selectOne(any())).thenReturn(enabledClient("https://app.com/callback"));
        // 前缀匹配可被 https://app.com/callback.evil.com 绕过，必须精确匹配
        assertThrows(BusinessException.class, () ->
                ssoAuthService.authorize("app-1", "https://app.com/callback.evil.com"));
    }

    @Test
    void authorizeAllowsExactRedirectMatch() {
        when(oauthClientMapper.selectOne(any())).thenReturn(enabledClient("https://app.com/callback"));
        when(jwtUtil.generateJti()).thenReturn("code-1");
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(1L);
            assertDoesNotThrow(() -> ssoAuthService.authorize("app-1", "https://app.com/callback"));
        }
    }

    @Test
    void tokenExchangeSetsTenantFromCodeBeforeUserQuery() {
        // R1-1.7：授权码携带租户段，兑换时先就位租户上下文再查用户，
        // 避免租户拦截器注入默认 tenant_id=1 使非租户 1 用户兑换失败。
        SysOauthClientDO client = enabledClient("https://app.com/callback");
        client.setClientSecret("secret");
        when(oauthClientMapper.selectOne(any())).thenReturn(client);
        when(jwtUtil.generateJti()).thenReturn("access-1");
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sso:code:code-1")).thenReturn("10:2:app-1");
        AtomicLong tenantAtUserQuery = new AtomicLong();
        SysUserDO user = new SysUserDO();
        user.setId(10L);
        user.setTenantId(2L);
        user.setUsername("zhangsan");
        user.setStatus(1);
        when(userMapper.selectById(10L)).thenAnswer(invocation -> {
            tenantAtUserQuery.set(TenantContext.getTenantId());
            return user;
        });
        when(roleMapper.selectRoleCodesByUserId(10L)).thenReturn(List.of());
        when(menuMapper.selectPermsByUserId(10L)).thenReturn(List.of());

        SsoTokenRequest request = new SsoTokenRequest();
        request.setClientId("app-1");
        request.setClientSecret("secret");
        request.setCode("code-1");
        SsoTokenVo vo = ssoAuthService.token(request);

        assertThat(tenantAtUserQuery.get()).isEqualTo(2L);
        verify(jwtUtil).createAccessToken("access-1", 10L, "zhangsan", 2L, List.of(), List.of());
        assertThat(vo.getTokenType()).isEqualTo("Bearer");
    }
}
