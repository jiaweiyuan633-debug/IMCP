package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.common.BusinessException;
import com.example.admin.module.auth.entity.SysOauthClientDO;
import com.example.admin.module.auth.mapper.SysOauthClientMapper;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
}
