package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.auth.dto.SsoTokenRequest;
import com.example.admin.module.auth.entity.SysOauthClientDO;
import com.example.admin.module.auth.mapper.SysOauthClientMapper;
import com.example.admin.module.auth.vo.SsoAuthorizeVo;
import com.example.admin.module.auth.vo.SsoTokenVo;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.security.JwtProperties;
import com.example.admin.security.JwtUtil;
import com.example.admin.security.SecurityUtils;
import com.example.admin.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * SSO 授权码服务：本平台作为 OAuth2 授权服务，为注册的第三方应用签发一次性授权码与访问令牌。
 */
@Service
@RequiredArgsConstructor
public class SsoAuthService {

    private static final String SSO_CODE_PREFIX = "sso:code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final SysOauthClientMapper oauthClientMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;

    /** 当前登录用户授权某第三方应用，签发一次性授权码。 */
    public SsoAuthorizeVo authorize(String clientId, String redirectUri) {
        SysOauthClientDO client = requireEnabledClient(clientId);
        // redirect_uri 必须与注册白名单精确一致：前缀匹配存在开放重定向绕过（如 app.com/callback.evil.com）
        if (!StringUtils.hasText(client.getRedirectUri()) || !client.getRedirectUri().equals(redirectUri)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "redirect_uri 不在应用白名单内");
        }
        String code = jwtUtil.generateJti();
        // 授权码绑定用户、租户与客户端，一次性消费
        // （R1-1.7 增加租户段：兑换时先就位租户上下文再查用户，避免非租户 1 用户查不到）
        redisTemplate.opsForValue().set(
                SSO_CODE_PREFIX + code,
                SecurityUtils.getUserId() + ":" + TenantContext.getTenantId() + ":" + clientId, CODE_TTL);
        return SsoAuthorizeVo.builder()
                .code(code)
                .redirectUri(client.getRedirectUri())
                .build();
    }

    /** 第三方应用用授权码换访问令牌。 */
    public SsoTokenVo token(SsoTokenRequest request) {
        SysOauthClientDO client = requireEnabledClient(request.getClientId());
        if (!client.getClientSecret().equals(request.getClientSecret())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "client_secret 无效");
        }
        String value = redisTemplate.opsForValue().get(SSO_CODE_PREFIX + request.getCode());
        if (value == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "授权码无效或已过期");
        }
        redisTemplate.delete(SSO_CODE_PREFIX + request.getCode());
        String[] parts = value.split(":");
        if (parts.length != 3 || !parts[2].equals(request.getClientId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "授权码与客户端不匹配");
        }
        Long userId = Long.valueOf(parts[0]);
        // R1-1.7：授权码携带租户，兑换时先就位租户上下文再查用户，
        // 避免租户拦截器注入默认 tenant_id=1 查不到非租户 1 用户。
        TenantContext.setTenantId(Long.valueOf(parts[1]));
        SysUserDO user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        TenantContext.setTenantId(user.getTenantId());
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        List<String> perms = menuMapper.selectPermsByUserId(userId);
        String accessJti = jwtUtil.generateJti();
        String accessToken = jwtUtil.createAccessToken(accessJti, userId, user.getUsername(),
                user.getTenantId(), roles, perms);
        // SSO 令牌不签发 refresh token，用空串占位避免 Redis 值判空异常
        tokenService.saveAccessToken(accessJti, "");
        return SsoTokenVo.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpireMinutes() * 60L)
                .scope(client.getScope())
                .build();
    }

    private SysOauthClientDO requireEnabledClient(String clientId) {
        SysOauthClientDO client = oauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientDO>()
                .eq(SysOauthClientDO::getClientId, clientId));
        if (client == null || client.getEnabled() == null || client.getEnabled() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "应用不存在或已停用");
        }
        return client;
    }
}
