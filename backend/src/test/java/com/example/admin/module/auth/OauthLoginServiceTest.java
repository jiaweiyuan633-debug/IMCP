package com.example.admin.module.auth;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.auth.dto.OauthBindRequest;
import com.example.admin.module.auth.entity.SysUserOauthDO;
import com.example.admin.module.auth.mapper.SysOauthConfigMapper;
import com.example.admin.module.auth.mapper.SysUserOauthMapper;
import com.example.admin.module.auth.vo.LoginResponse;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4-1.19 回归测试：匿名绑定端点必须以绑定凭证携带的配置租户定位平台账号。
 *
 * <p>背景：{@link OauthLoginService#bind} 为匿名端点（无租户上下文），旧代码
 * {@code userMapper.selectOne(eq(username))} 会被租户拦截器注入默认 tenant_id=1，
 * 租户 2 的平台账号按用户名永远查不到、绑定必失败。修复后改走 R1-1.7 的跨租户辅助方法
 * {@code selectByUsername(username, bindData.getTenantId())}，租户来源与
 * findBinding/bindToUser 一致。本测试断言绑定查询按凭证租户定位、绑定行落到正确租户。
 */
class OauthLoginServiceTest {

    private SysOauthConfigMapper oauthConfigMapper;
    private SysUserOauthMapper userOauthMapper;
    private SysUserMapper userMapper;
    private AuthService authService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RestTemplate restTemplate;
    private PasswordEncoder passwordEncoder;
    private OauthLoginService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        oauthConfigMapper = mock(SysOauthConfigMapper.class);
        userOauthMapper = mock(SysUserOauthMapper.class);
        userMapper = mock(SysUserMapper.class);
        authService = mock(AuthService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        restTemplate = mock(RestTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new OauthLoginService(oauthConfigMapper, userOauthMapper, userMapper, authService,
                redisTemplate, new ObjectMapper(), restTemplate, passwordEncoder);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void bindLooksUpUserWithinBindTokenTenantAndInsertsBinding() {
        // 租户 2 的 GitHub 配置产生绑定凭证（tenantId=2）；若回退到 selectOne（拦截器注入
        // tenant_id=1），租户 2 用户查询为空、绑定抛 BAD_CREDENTIALS，此断言即失败
        stubBindToken("tok-1");
        when(userMapper.selectByUsername("zhangsan", 2L)).thenReturn(List.of(tenantUser(10L, 2L, "zhangsan")));
        when(passwordEncoder.matches("pwd-1", "encoded-pass")).thenReturn(true);
        LoginResponse login = LoginResponse.builder().accessToken("at-1").build();
        when(authService.completeLogin(any(SysUserDO.class), any(HttpServletRequest.class))).thenReturn(login);

        LoginResponse result = service.bind(bindRequest("tok-1", "zhangsan", "pwd-1"), mock(HttpServletRequest.class));

        // 必须走跨租户辅助方法并按凭证租户限定
        verify(userMapper).selectByUsername("zhangsan", 2L);
        ArgumentCaptor<SysUserOauthDO> captor = ArgumentCaptor.forClass(SysUserOauthDO.class);
        verify(userOauthMapper).insert(captor.capture());
        SysUserOauthDO row = captor.getValue();
        assertThat(row.getTenantId()).isEqualTo(2L);
        assertThat(row.getUserId()).isEqualTo(10L);
        assertThat(row.getProvider()).isEqualTo("github");
        assertThat(row.getOpenId()).isEqualTo("gh-123");
        assertThat(result.getAccessToken()).isEqualTo("at-1");
    }

    @Test
    void bindRejectsWhenPasswordMismatch() {
        stubBindToken("tok-1");
        when(userMapper.selectByUsername("zhangsan", 2L)).thenReturn(List.of(tenantUser(10L, 2L, "zhangsan")));
        when(passwordEncoder.matches("wrong", "encoded-pass")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(bindRequest("tok-1", "zhangsan", "wrong"), mock(HttpServletRequest.class)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_CREDENTIALS.getCode());
        verify(userOauthMapper, never()).insert(any(SysUserOauthDO.class));
    }

    @Test
    void bindRejectsWhenUserNotFoundInBindTokenTenant() {
        stubBindToken("tok-1");
        when(userMapper.selectByUsername("zhangsan", 2L)).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(bindRequest("tok-1", "zhangsan", "pwd-1"), mock(HttpServletRequest.class)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.BAD_CREDENTIALS.getCode());
        verify(userOauthMapper, never()).insert(any(SysUserOauthDO.class));
    }

    private void stubBindToken(String token) {
        when(valueOps.get("oauth:bind:" + token)).thenReturn(
                "{\"provider\":\"github\",\"tenantId\":2,\"openId\":\"gh-123\",\"unionId\":null,"
                        + "\"nickname\":\"zhangsan-github\",\"avatar\":null}");
    }

    private SysUserDO tenantUser(long id, long tenantId, String username) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword("encoded-pass");
        user.setStatus(1);
        return user;
    }

    private OauthBindRequest bindRequest(String token, String username, String password) {
        OauthBindRequest request = new OauthBindRequest();
        request.setBindToken(token);
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
