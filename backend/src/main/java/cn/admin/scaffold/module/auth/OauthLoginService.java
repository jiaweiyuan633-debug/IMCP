package cn.admin.scaffold.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.auth.dto.OauthAuthorizeUrlRequest;
import cn.admin.scaffold.module.auth.dto.OauthBindRequest;
import cn.admin.scaffold.module.auth.entity.SysOauthConfigDO;
import cn.admin.scaffold.module.auth.entity.SysUserOauthDO;
import cn.admin.scaffold.module.auth.mapper.SysOauthConfigMapper;
import cn.admin.scaffold.module.auth.mapper.SysUserOauthMapper;
import cn.admin.scaffold.module.auth.vo.LoginResponse;
import cn.admin.scaffold.module.auth.vo.OauthBindingVo;
import cn.admin.scaffold.module.auth.vo.OauthProviderVo;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import cn.admin.scaffold.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第三方 OAuth2 登录服务：授权跳转、回调换 token、绑定/解绑与扫码登录。
 * 登录态通过一次性 ticket 重定向给前端，避免 access token 暴露在 URL。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OauthLoginService {

    private static final String STATE_KEY_PREFIX = "oauth:state:";
    private static final String BIND_KEY_PREFIX = "oauth:bind:";
    private static final String LOGIN_TICKET_PREFIX = "oauth:login-ticket:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration BIND_TTL = Duration.ofMinutes(15);
    private static final Duration LOGIN_TICKET_TTL = Duration.ofSeconds(60);
    // 匿名绑定端点以「用户名+密码」验证平台账号，与密码登录同等级防暴力破解。
    // 失败锁定键带租户——(tenant_id, username) 才唯一（V33），跨租户同名不互锁、不误伤。
    private static final String BIND_FAIL_KEY_PREFIX = "oauth:bind:fail:";
    private static final String BIND_RATE_KEY_PREFIX = "oauth:bind:rate:";
    private static final int MAX_BIND_FAILURES = 5;
    private static final int BIND_RATE_LIMIT_PER_MINUTE = 20;
    private static final long BIND_RATE_WINDOW_MINUTES = 1;
    private static final long BIND_FAIL_LOCK_MINUTES = 10;

    private final SysOauthConfigMapper oauthConfigMapper;
    private final SysUserOauthMapper userOauthMapper;
    private final SysUserMapper userMapper;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecretCipher secretCipher;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.callback-base-url:http://127.0.0.1:8080}")
    private String callbackBaseUrl;

    public List<OauthProviderVo> providers() {
        List<SysOauthConfigDO> configs = oauthConfigMapper.selectList(
                new LambdaQueryWrapper<SysOauthConfigDO>()
                        .eq(SysOauthConfigDO::getEnabled, 1)
                        .orderByAsc(SysOauthConfigDO::getSort)
                        .orderByAsc(SysOauthConfigDO::getId));
        return configs.stream().map(c -> OauthProviderVo.builder()
                .provider(c.getProvider())
                .label(providerLabel(c.getProvider()))
                .enabled(true)
                .build()).toList();
    }

    /** 生成第三方授权跳转地址。bindMode=true 表示已登录用户发起绑定。 */
    public String buildAuthorizeUrl(OauthAuthorizeUrlRequest request) {
        OauthProvider provider = OauthProvider.fromCode(request.getProvider());
        SysOauthConfigDO config = requireEnabled(provider);
        String state = UUID.randomUUID().toString().replace("-", "");
        StateData stateData = new StateData();
        stateData.setProvider(provider.getCode());
        stateData.setBindMode(Boolean.TRUE.equals(request.getBindMode()));
        if (stateData.getBindMode()) {
            Long userId = SecurityUtils.tryGetUserId();
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED);
            }
            stateData.setUserId(userId);
        }
        redisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, toJson(stateData), STATE_TTL);
        String redirectUri = resolveRedirectUri(config, provider);
        String scope = StringUtils.hasText(config.getScope()) ? config.getScope() : defaultScope(provider);
        String encodedRedirect = urlEncode(redirectUri);
        if (provider == OauthProvider.WECHAT) {
            return provider.getAuthorizeUrl()
                    + "?appid=" + config.getAppId()
                    + "&redirect_uri=" + encodedRedirect
                    + "&response_type=code"
                    + "&scope=" + scope
                    + "&state=" + state + "#wechat_redirect";
        }
        return provider.getAuthorizeUrl()
                + "?client_id=" + config.getAppId()
                + "&redirect_uri=" + encodedRedirect
                + "&response_type=code"
                + "&scope=" + scope
                + "&state=" + state;
    }

    /** 第三方回调入口：校验 state、换取 token，重定向到前端并携带一次性登录态。 */
    public String callback(String providerCode, String code, String state, HttpServletRequest httpRequest) {
        StateData stateData = consumeState(state);
        if (stateData == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "state 无效或已过期");
        }
        if (!providerCode.equalsIgnoreCase(stateData.getProvider())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "state 与提供方不匹配");
        }
        OauthProvider provider = OauthProvider.fromCode(providerCode);
        SysOauthConfigDO config = requireEnabled(provider);

        OauthToken token = fetchAccessToken(provider, config, code);
        ProviderUser userInfo = fetchUserInfo(provider, config, token);

        // 已登录用户发起绑定：直接落库并回跳
        if (Boolean.TRUE.equals(stateData.getBindMode()) && stateData.getUserId() != null) {
            bindToUser(stateData.getUserId(), providerCode, config.getTenantId(), userInfo);
            return frontendUrl + "/oauth/callback?bound=true";
        }

        SysUserOauthDO binding = findBinding(config.getTenantId(), providerCode, userInfo.getOpenId());
        if (binding != null) {
            SysUserDO user = userMapper.selectById(binding.getUserId());
            if (user == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND);
            }
            LoginResponse loginResponse = authService.completeLogin(user, httpRequest);
            // 一次性 ticket 传递登录态，避免 token 暴露在 URL
            String ticket = UUID.randomUUID().toString().replace("-", "");
            redisTemplate.opsForValue().set(LOGIN_TICKET_PREFIX + ticket, toJson(loginResponse), LOGIN_TICKET_TTL);
            return frontendUrl + "/oauth/callback?ticket=" + ticket;
        }

        // 未绑定：返回一次性绑定凭证，前端引导完成账号绑定
        BindData bindData = new BindData();
        bindData.setProvider(providerCode);
        bindData.setTenantId(config.getTenantId());
        bindData.setOpenId(userInfo.getOpenId());
        bindData.setUnionId(userInfo.getUnionId());
        bindData.setNickname(userInfo.getNickname());
        bindData.setAvatar(userInfo.getAvatar());
        String bindToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(BIND_KEY_PREFIX + bindToken, toJson(bindData), BIND_TTL);
        return frontendUrl + "/oauth/callback?bindToken=" + bindToken
                + "&provider=" + providerCode
                + "&providerLabel=" + urlEncode(provider.getLabel());
    }

    /** 消费一次性登录 ticket，返回登录结果。 */
    public LoginResponse consumeLoginTicket(String ticket) {
        String json = redisTemplate.opsForValue().get(LOGIN_TICKET_PREFIX + ticket);
        if (json == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "登录凭证无效或已过期");
        }
        redisTemplate.delete(LOGIN_TICKET_PREFIX + ticket);
        return fromJson(json, LoginResponse.class);
    }

    /** 绑定第三方账号到平台账号并登录。 */
    public LoginResponse bind(OauthBindRequest request, HttpServletRequest httpRequest) {
        // 匿名绑定端点以「用户名+密码」验证平台账号，防护须与密码登录同等级——
        // IP 级限流防撒网爆破（取 socket 真实地址，与 ApiRateLimitInterceptor 同源原则）+ 用户名失败锁定。
        if (isBindRateLimited(httpRequest.getRemoteAddr())) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        BindData bindData = consumeBindToken(request.getBindToken());
        if (bindData == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "绑定凭证无效或已过期");
        }
        checkBindLockout(bindData.getTenantId(), request.getUsername());
        // 匿名绑定端点无租户上下文，selectOne 会被租户拦截器注入默认 tenant_id=1，
        // 租户 2 的平台账号按用户名永远查不到、绑定必失败。改走跨租户辅助方法，
        // 并以绑定凭证携带的配置租户精确限定（与 findBinding/bindToUser 同一租户来源）。
        List<SysUserDO> users = userMapper.selectByUsername(request.getUsername(), bindData.getTenantId());
        SysUserDO user = users.isEmpty() ? null : users.get(0);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordBindFailure(bindData.getTenantId(), request.getUsername());
            throw new BusinessException(ResultCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        bindToUser(user.getId(), bindData.getProvider(), bindData.getTenantId(), bindData);
        clearBindFailures(bindData.getTenantId(), request.getUsername());
        return authService.completeLogin(user, httpRequest);
    }

    public List<OauthBindingVo> bindings() {
        Long userId = SecurityUtils.getUserId();
        List<SysUserOauthDO> rows = userOauthMapper.selectList(new LambdaQueryWrapper<SysUserOauthDO>()
                .eq(SysUserOauthDO::getTenantId, TenantContext.getTenantId())
                .eq(SysUserOauthDO::getUserId, userId)
                .orderByAsc(SysUserOauthDO::getId));
        return rows.stream().map(r -> OauthBindingVo.builder()
                .provider(r.getProvider())
                .providerLabel(providerLabel(r.getProvider()))
                .openId(r.getOpenId())
                .nickname(r.getNickname())
                .avatar(r.getAvatar())
                .createdAt(r.getCreatedAt())
                .build()).toList();
    }

    public void unbind(String providerCode) {
        OauthProvider.fromCode(providerCode);
        Long userId = SecurityUtils.getUserId();
        int deleted = userOauthMapper.delete(new LambdaQueryWrapper<SysUserOauthDO>()
                .eq(SysUserOauthDO::getTenantId, TenantContext.getTenantId())
                .eq(SysUserOauthDO::getUserId, userId)
                .eq(SysUserOauthDO::getProvider, providerCode));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
    }

    // ---------- 第三方 HTTP 交互 ----------

    private OauthToken fetchAccessToken(OauthProvider provider, SysOauthConfigDO config, String code) {
        try {
            // appSecret 落库为 AES-GCM 密文，调用第三方前解密（存量明文经 SecretCipher 原样放行）
            String appSecret = secretCipher.decrypt(config.getAppSecret());
            if (provider == OauthProvider.WECHAT) {
                String url = provider.getTokenUrl()
                        + "?appid=" + config.getAppId()
                        + "&secret=" + appSecret
                        + "&code=" + code
                        + "&grant_type=authorization_code";
                Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
                ensureTokenOk(resp, provider);
                OauthToken token = new OauthToken();
                token.setAccessToken(str(resp, "access_token"));
                token.setOpenId(str(resp, "openid"));
                return token;
            }
            // GitHub / Gitee：表单 POST 换 token
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", config.getAppId());
            body.add("client_secret", appSecret);
            body.add("code", code);
            if (provider == OauthProvider.GITEE) {
                body.add("grant_type", "authorization_code");
                body.add("redirect_uri", resolveRedirectUri(config, provider));
            }
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    provider.getTokenUrl(), new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> resp = response.getBody();
            ensureTokenOk(resp, provider);
            OauthToken token = new OauthToken();
            token.setAccessToken(str(resp, "access_token"));
            return token;
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "第三方认证服务调用失败");
        }
    }

    private ProviderUser fetchUserInfo(OauthProvider provider, SysOauthConfigDO config, OauthToken token) {
        try {
            if (provider == OauthProvider.WECHAT) {
                String url = provider.getUserInfoUrl()
                        + "?access_token=" + token.getAccessToken()
                        + "&openid=" + token.getOpenId();
                Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
                ensureTokenOk(resp, provider);
                ProviderUser user = new ProviderUser();
                user.setOpenId(str(resp, "openid"));
                user.setUnionId(str(resp, "unionid"));
                user.setNickname(str(resp, "nickname"));
                user.setAvatar(str(resp, "headimgurl"));
                return user;
            }
            if (provider == OauthProvider.GITEE) {
                String url = provider.getUserInfoUrl() + "?access_token=" + token.getAccessToken();
                Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
                return fromGithubStyle(resp);
            }
            // GitHub：Bearer 头
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token.getAccessToken());
            ResponseEntity<Map> response = restTemplate.exchange(
                    provider.getUserInfoUrl(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return fromGithubStyle(response.getBody());
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "第三方用户信息获取失败");
        }
    }

    private ProviderUser fromGithubStyle(Map<String, Object> resp) {
        ProviderUser user = new ProviderUser();
        user.setOpenId(str(resp, "id"));
        String name = str(resp, "name");
        user.setNickname(StringUtils.hasText(name) ? name : str(resp, "login"));
        user.setAvatar(str(resp, "avatar_url"));
        return user;
    }

    // ---------- 绑定落库 ----------

    private void bindToUser(Long userId, String providerCode, Long tenantId, ProviderUser userInfo) {
        SysUserOauthDO existing = userOauthMapper.selectOne(new LambdaQueryWrapper<SysUserOauthDO>()
                .eq(SysUserOauthDO::getTenantId, tenantId)
                .eq(SysUserOauthDO::getUserId, userId)
                .eq(SysUserOauthDO::getProvider, providerCode));
        if (existing != null) {
            existing.setOpenId(userInfo.getOpenId());
            existing.setUnionId(userInfo.getUnionId());
            existing.setNickname(userInfo.getNickname());
            existing.setAvatar(userInfo.getAvatar());
            userOauthMapper.updateById(existing);
            return;
        }
        SysUserOauthDO row = new SysUserOauthDO();
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setProvider(providerCode);
        row.setOpenId(userInfo.getOpenId());
        row.setUnionId(userInfo.getUnionId());
        row.setNickname(userInfo.getNickname());
        row.setAvatar(userInfo.getAvatar());
        userOauthMapper.insert(row);
    }

    private void bindToUser(Long userId, String providerCode, Long tenantId, BindData bindData) {
        SysUserOauthDO row = new SysUserOauthDO();
        row.setTenantId(tenantId);
        row.setUserId(userId);
        row.setProvider(providerCode);
        row.setOpenId(bindData.getOpenId());
        row.setUnionId(bindData.getUnionId());
        row.setNickname(bindData.getNickname());
        row.setAvatar(bindData.getAvatar());
        userOauthMapper.insert(row);
    }

    // ---------- Redis 状态 ----------

    private StateData consumeState(String state) {
        String json = redisTemplate.opsForValue().get(STATE_KEY_PREFIX + state);
        if (json == null) {
            return null;
        }
        redisTemplate.delete(STATE_KEY_PREFIX + state);
        return fromJson(json, StateData.class);
    }

    private BindData consumeBindToken(String bindToken) {
        String json = redisTemplate.opsForValue().get(BIND_KEY_PREFIX + bindToken);
        if (json == null) {
            return null;
        }
        redisTemplate.delete(BIND_KEY_PREFIX + bindToken);
        return fromJson(json, BindData.class);
    }

    // ---------- 绑定暴力破解防护（与 AuthService 登录防护同等级） ----------

    private boolean isBindRateLimited(String ip) {
        String key = BIND_RATE_KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(BIND_RATE_WINDOW_MINUTES));
        }
        return count != null && count > BIND_RATE_LIMIT_PER_MINUTE;
    }

    private void checkBindLockout(Long tenantId, String username) {
        String value = redisTemplate.opsForValue().get(bindFailKey(tenantId, username));
        if (value != null && Integer.parseInt(value) >= MAX_BIND_FAILURES) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
    }

    private void recordBindFailure(Long tenantId, String username) {
        String key = bindFailKey(tenantId, username);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(BIND_FAIL_LOCK_MINUTES));
        }
    }

    private void clearBindFailures(Long tenantId, String username) {
        redisTemplate.delete(bindFailKey(tenantId, username));
    }

    private static String bindFailKey(Long tenantId, String username) {
        return BIND_FAIL_KEY_PREFIX + tenantId + ":" + username;
    }

    // ---------- 工具 ----------

    private SysOauthConfigDO requireEnabled(OauthProvider provider) {
        SysOauthConfigDO config = oauthConfigMapper.selectOne(new LambdaQueryWrapper<SysOauthConfigDO>()
                .eq(SysOauthConfigDO::getProvider, provider.getCode()));
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "第三方登录未配置或已停用: " + provider.getLabel());
        }
        return config;
    }

    private SysUserOauthDO findBinding(Long tenantId, String providerCode, String openId) {
        return userOauthMapper.selectOne(new LambdaQueryWrapper<SysUserOauthDO>()
                .eq(SysUserOauthDO::getTenantId, tenantId)
                .eq(SysUserOauthDO::getProvider, providerCode)
                .eq(SysUserOauthDO::getOpenId, openId));
    }

    private String resolveRedirectUri(SysOauthConfigDO config, OauthProvider provider) {
        if (StringUtils.hasText(config.getRedirectUri())) {
            return config.getRedirectUri();
        }
        return callbackBaseUrl + "/api/auth/oauth/callback/" + provider.getCode();
    }

    private String defaultScope(OauthProvider provider) {
        if (provider == OauthProvider.WECHAT) {
            return "snsapi_login";
        }
        if (provider == OauthProvider.GITHUB) {
            return "read:user";
        }
        return "user_info";
    }

    private String providerLabel(String providerCode) {
        try {
            return OauthProvider.fromCode(providerCode).getLabel();
        } catch (IllegalArgumentException exception) {
            return providerCode;
        }
    }

    private void ensureTokenOk(Map<String, Object> resp, OauthProvider provider) {
        if (resp == null || !StringUtils.hasText(str(resp, "access_token"))) {
            String detail = str(resp, "errmsg");
            if (!StringUtils.hasText(detail)) {
                detail = str(resp, "error_description");
            }
            if (!StringUtils.hasText(detail)) {
                detail = str(resp, "error");
            }
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    provider.getLabel() + " 认证失败" + (StringUtils.hasText(detail) ? "：" + detail : ""));
        }
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "登录态数据解析失败");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ---------- 内部结构 ----------

    /** 授权跳转前的 state 载荷。 */
    @Data
    public static class StateData {
        private String provider;
        private Boolean bindMode;
        private Long userId;
    }

    /** 未绑定用户的一次性绑定凭证载荷。 */
    @Data
    public static class BindData {
        private String provider;
        private Long tenantId;
        private String openId;
        private String unionId;
        private String nickname;
        private String avatar;
    }

    /** 第三方 access_token 响应。 */
    @Data
    public static class OauthToken {
        private String accessToken;
        private String openId;
    }

    /** 第三方用户信息。 */
    @Data
    public static class ProviderUser {
        private String openId;
        private String unionId;
        private String nickname;
        private String avatar;
    }
}
