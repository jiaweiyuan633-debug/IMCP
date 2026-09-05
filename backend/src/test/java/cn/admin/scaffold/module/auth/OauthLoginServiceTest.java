package cn.admin.scaffold.module.auth;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.module.auth.dto.OauthBindRequest;
import cn.admin.scaffold.module.auth.entity.SysUserOauthDO;
import cn.admin.scaffold.module.auth.mapper.SysOauthConfigMapper;
import cn.admin.scaffold.module.auth.mapper.SysUserOauthMapper;
import cn.admin.scaffold.module.auth.vo.LoginResponse;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：匿名绑定端点必须以绑定凭证携带的配置租户定位平台账号。
 *
 * <p>背景：{@link OauthLoginService#bind} 为匿名端点（无租户上下文），旧代码
 * {@code userMapper.selectOne(eq(username))} 会被租户拦截器注入默认 tenant_id=1，
 * 租户 2 的平台账号按用户名永远查不到、绑定必失败。修复后改走跨租户辅助方法
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
                redisTemplate, new ObjectMapper(), restTemplate, passwordEncoder, mock(SecretCipher.class));
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
        // 绑定成功必须清除失败计数，解锁账号
        verify(redisTemplate).delete("oauth:bind:fail:2:zhangsan");
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

    @Test
    void bindRejectsWhenFailureCountLocked() {
        // 同一 (租户,用户名) 失败计数达阈值后绑定被直接拒绝，且不触碰用户查询（防单账号爆破）
        stubBindToken("tok-1");
        when(valueOps.get("oauth:bind:fail:2:zhangsan")).thenReturn("5");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(bindRequest("tok-1", "zhangsan", "pwd-1"), mock(HttpServletRequest.class)));

        assertThat(ex.getCode()).isEqualTo(ResultCode.LOGIN_TOO_MANY.getCode());
        verify(userMapper, never()).selectByUsername(anyString(), anyLong());
        verify(userOauthMapper, never()).insert(any(SysUserOauthDO.class));
    }

    @Test
    void bindRejectsWhenIpRateLimited() {
        // IP 级限流在消费绑定凭证之前触发（防撒网爆破），无需有效凭证即可拒绝
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(valueOps.increment("oauth:bind:rate:203.0.113.9")).thenReturn(21L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.bind(bindRequest("tok-any", "zhangsan", "pwd-1"), request));

        assertThat(ex.getCode()).isEqualTo(ResultCode.LOGIN_TOO_MANY.getCode());
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
