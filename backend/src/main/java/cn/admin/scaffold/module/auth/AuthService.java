package cn.admin.scaffold.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.config.SecurityProperties;
import cn.admin.scaffold.module.auth.dto.ChangePasswordRequest;
import cn.admin.scaffold.module.auth.dto.LoginRequest;
import cn.admin.scaffold.module.auth.dto.ProfileUpdateRequest;
import cn.admin.scaffold.module.auth.dto.TotpCodeRequest;
import cn.admin.scaffold.module.auth.vo.LoginResponse;
import cn.admin.scaffold.module.auth.vo.LoginConfigVo;
import cn.admin.scaffold.module.auth.vo.TotpStatusVo;
import cn.admin.scaffold.module.auth.vo.UserInfoVo;
import cn.admin.scaffold.module.monitor.vo.OnlineUserVo;
import cn.admin.scaffold.module.system.entity.SysLoginLogDO;
import cn.admin.scaffold.module.system.entity.SysConfigDO;
import cn.admin.scaffold.module.system.entity.SysMenuDO;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysConfigMapper;
import cn.admin.scaffold.module.system.mapper.SysLoginLogMapper;
import cn.admin.scaffold.module.system.mapper.SysMenuMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import cn.admin.scaffold.module.system.vo.MenuVo;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.security.JwtUtil;
import cn.admin.scaffold.security.LoginUser;
import cn.admin.scaffold.security.SecurityUtils;
import cn.admin.scaffold.security.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String CAPTCHA_CONFIG_KEY = "sys.account.captchaEnabled";
    private static final String LOGIN_RATE_KEY_PREFIX = "login:rate:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";
    private static final int RATE_LIMIT_PER_MINUTE = 20;
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long RATE_LIMIT_WINDOW_MINUTES = 1;
    private static final long FAILURE_LOCK_MINUTES = 10;
    /** 失败锁定指数退避封顶（分钟）：超过阈值后每次继续失败 10→20→40→80 封顶。 */
    private static final long MAX_LOCK_MINUTES = 80;
    private static final int LOGIN_SUCCESS = 1;
    private static final int LOGIN_FAILURE = 0;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysConfigMapper configMapper;
    private final CaptchaService captchaService;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final BusinessMetrics businessMetrics;
    private final SecurityProperties securityProperties;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (isRateLimited(ip)) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        checkLoginLockout(request.getUsername(), request.getTenantId());
        if (captchaEnabled() && !captchaService.verify(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        String username = request.getUsername().trim();
        // R1-1.7：登录查询必须跨租户——此时租户上下文尚未就位，租户拦截器注入默认 tenant_id=1
        // 会把非租户 1 用户挡在门外。selectByUsername 豁免租户拦截器，按用户名（+ 可选租户）定位。
        List<SysUserDO> candidates = userMapper.selectByUsername(username, request.getTenantId());
        if (candidates.size() > 1) {
            // 跨租户同名且未指定租户，无法唯一定位登录账号
            saveLoginLog(httpRequest, username, false, "存在同名账号，需指定租户");
            recordLoginFailure(username, request.getTenantId());
            throw new BusinessException(ResultCode.USERNAME_AMBIGUOUS);
        }
        SysUserDO user = candidates.isEmpty() ? null : candidates.get(0);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            saveLoginLog(httpRequest, username, false, "用户名或密码错误");
            recordLoginFailure(username, request.getTenantId());
            throw new BusinessException(ResultCode.BAD_CREDENTIALS);
        }
        TenantContext.setTenantId(user.getTenantId());
        if (user.getStatus() == null || user.getStatus() != 1) {
            saveLoginLog(httpRequest, username, false, "账号已被禁用");
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (user.getTotpEnabled() != null && user.getTotpEnabled() == 1
                && !totpService.verify(totpService.decrypt(user.getTotpSecret()), request.getTotpCode())) {
            // TOTP 校验失败计入失败锁定，防止对 6 位动态码实施分布式暴力破解
            saveLoginLog(httpRequest, username, false, "动态验证码错误");
            recordLoginFailure(username, request.getTenantId());
            throw new BusinessException(ResultCode.TOTP_REQUIRED);
        }
        // R4-1.39：失败计数按租户维度落键，成功登录须同时清空请求租户键与用户实际租户键
        clearLoginFailures(username, request.getTenantId(), user.getTenantId());
        return completeLogin(user, httpRequest);
    }

    /**
     * 签发令牌并记录登录态：供密码登录与第三方 OAuth 登录复用。
     * 调用方需保证 user 已通过鉴权校验（密码/TOTP/第三方绑定）且启用。
     */
    public LoginResponse completeLogin(SysUserDO user, HttpServletRequest httpRequest) {
        TenantContext.setTenantId(user.getTenantId());
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = menuMapper.selectPermsByUserId(user.getId());
        List<MenuVo> menus = buildMenuTree(menuMapper.selectMenusByUserId(user.getId()));
        String accessJti = jwtUtil.generateJti();
        String refreshJti = jwtUtil.generateJti();

        String accessToken = jwtUtil.createAccessToken(accessJti, user.getId(), user.getUsername(),
                user.getTenantId(), roles, perms);
        String refreshToken = jwtUtil.createRefreshToken(refreshJti, user.getId(), user.getUsername(),
                user.getTenantId());
        tokenService.saveAccessToken(accessJti, refreshJti);
        tokenService.saveRefreshToken(refreshJti, String.valueOf(user.getId()));
        tokenService.saveOnlineUser(accessJti, OnlineUserVo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .ip(httpRequest.getRemoteAddr())
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build());

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        saveLoginLog(httpRequest, user.getUsername(), true, "登录成功");

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserInfo(user, roles, perms, menus))
                .mustChangePassword(mustChangePassword(user))
                .build();
    }

    public LoginConfigVo loginConfig() {
        SysConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, CAPTCHA_CONFIG_KEY));
        return LoginConfigVo.builder()
                .captchaEnabled(config != null && Boolean.parseBoolean(config.getConfigValue()))
                .build();
    }

    public LoginResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(refreshToken);
        String refreshJti = claims.getId();
        // R4-1.44：原子消费（GETDEL）替代 hasKey+delete 两步——并发用同一被窃 refresh token
        // 刷新时，原实现两个请求均通过存在性检查后各自签发新令牌对，轮换形同虚设；消费返回
        // null 即拒绝（不存在/已被并发消费）。消费先行也使「用户被禁用/删除后用旧 token 反复
        // 刷新探测」不再成立：首次失败即作废该令牌。
        String storedUserId = tokenService.consumeRefreshToken(refreshJti);
        if (storedUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Long userId = Long.valueOf(claims.getSubject());
        // R1-1.7：refresh token 携带 tenantId，查询用户前先就位租户上下文，
        // 避免 selectById 被租户拦截器注入默认 tenant_id=1 查不到非租户 1 用户。
        Long tokenTenantId = asLong(claims.get("tenantId"));
        if (tokenTenantId != null) {
            TenantContext.setTenantId(tokenTenantId);
        }
        SysUserDO user = userMapper.selectById(userId);
        // DB 为用户租户权威来源：用户被迁移租户后以库表为准，token 仅作查询前置。
        if (user != null && user.getTenantId() != null) {
            TenantContext.setTenantId(user.getTenantId());
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // refresh token 已在最前原子消费，此处不再二次删除
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        List<String> perms = menuMapper.selectPermsByUserId(userId);
        List<MenuVo> menus = buildMenuTree(menuMapper.selectMenusByUserId(userId));
        String accessJti = jwtUtil.generateJti();
        String newRefreshJti = jwtUtil.generateJti();

        String accessToken = jwtUtil.createAccessToken(accessJti, userId, user.getUsername(),
                user.getTenantId(), roles, perms);
        String newRefreshToken = jwtUtil.createRefreshToken(newRefreshJti, userId, user.getUsername(),
                user.getTenantId());
        tokenService.saveAccessToken(accessJti, newRefreshJti);
        tokenService.saveRefreshToken(newRefreshJti, String.valueOf(userId));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .user(toUserInfo(user, roles, perms, menus))
                .mustChangePassword(mustChangePassword(user))
                .build();
    }

    public void logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(token);
        tokenService.revokeAccessToken(claims.getId());
    }

    public UserInfoVo me() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUserDO user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        List<MenuVo> menus = buildMenuTree(menuMapper.selectMenusByUserId(user.getId()));
        return toUserInfo(user, loginUser.getRoles(), loginUser.getPerms(), menus);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUserDO user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // 改密成功后清除"必须改密"标记并记录改密时间（密码过期策略从此刻重新计时）
        user.setMustChangePassword(0);
        user.setPasswordChangedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void updateProfile(ProfileUpdateRequest request) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUserDO user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        userMapper.updateById(user);
    }

    public TotpStatusVo totpStatus() {
        SysUserDO user = getCurrentUser();
        boolean enabled = user.getTotpEnabled() != null && user.getTotpEnabled() == 1;
        return TotpStatusVo.builder().enabled(enabled).build();
    }

    public TotpStatusVo setupTotp() {
        SysUserDO user = getCurrentUser();
        String secret = totpService.generateSecret();
        user.setTotpSecret(totpService.encrypt(secret));
        user.setTotpEnabled(0);
        userMapper.updateById(user);
        return TotpStatusVo.builder()
                .enabled(false)
                .secret(secret)
                .otpauthUrl(totpService.buildOtpauthUrl(secret, user.getUsername()))
                .build();
    }

    public void enableTotp(TotpCodeRequest request) {
        SysUserDO user = getCurrentUser();
        if (!totpService.verify(totpService.decrypt(user.getTotpSecret()), request.getCode())) {
            throw new BusinessException(ResultCode.TOTP_CODE_ERROR);
        }
        user.setTotpEnabled(1);
        userMapper.updateById(user);
    }

    public void disableTotp(TotpCodeRequest request) {
        SysUserDO user = getCurrentUser();
        if (!totpService.verify(totpService.decrypt(user.getTotpSecret()), request.getCode())) {
            throw new BusinessException(ResultCode.TOTP_CODE_ERROR);
        }
        user.setTotpSecret(null);
        user.setTotpEnabled(0);
        userMapper.updateById(user);
    }

    private SysUserDO getCurrentUser() {
        SysUserDO user = userMapper.selectById(SecurityUtils.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private UserInfoVo toUserInfo(SysUserDO user, List<String> roles, List<String> perms, List<MenuVo> menus) {
        return UserInfoVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .roles(roles == null ? Collections.emptyList() : roles)
                .perms(perms == null ? Collections.emptyList() : perms)
                .menus(menus == null ? Collections.emptyList() : menus)
                .mustChangePassword(mustChangePassword(user))
                .build();
    }

    /**
     * 密码策略判定：是否强制用户改密（批次1·安全阻断）。
     *
     * <p>仅在 {@code SecurityProperties.forcePasswordChange} 开启时生效（生产默认开启，
     * 本地 dev/test 关闭以保持 admin/admin123 的开发与 CI 体验）。两种触发条件：
     * <ol>
     *   <li>V63 迁移标记的 {@code must_change_password=1}（仍使用默认种子口令的存量账号）；</li>
     *   <li>密码过期：{@code password_changed_at} 距今超过 {@code passwordExpireDays} 天
     *       （默认 90，0 表示禁用过期检查）。</li>
     * </ol>
     */
    private boolean mustChangePassword(SysUserDO user) {
        if (!securityProperties.isForcePasswordChange()) {
            return false;
        }
        boolean flagged = user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
        if (flagged) {
            return true;
        }
        int expireDays = securityProperties.getPasswordExpireDays();
        if (expireDays <= 0 || user.getPasswordChangedAt() == null) {
            return false;
        }
        return user.getPasswordChangedAt().plusDays(expireDays).isBefore(LocalDateTime.now());
    }

    private List<MenuVo> buildMenuTree(List<SysMenuDO> menus) {
        return buildChildren(menus, 0L);
    }

    private List<MenuVo> buildChildren(List<SysMenuDO> menus, Long parentId) {
        List<MenuVo> children = new ArrayList<>();
        for (SysMenuDO menu : menus) {
            if (parentId.equals(menu.getParentId())) {
                children.add(MenuVo.builder()
                        .id(menu.getId())
                        .parentId(menu.getParentId())
                        .name(menu.getName())
                        .type(menu.getType())
                        .path(menu.getPath())
                        .component(menu.getComponent())
                        .perm(menu.getPerm())
                        .icon(menu.getIcon())
                        .sort(menu.getSort())
                        .visible(menu.getVisible())
                        .status(menu.getStatus())
                        .children(buildChildren(menus, menu.getId()))
                        .build());
            }
        }
        return children;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(SecurityUtils.BEARER_PREFIX)) {
            return header.substring(SecurityUtils.BEARER_PREFIX.length());
        }
        return null;
    }

    private void saveLoginLog(HttpServletRequest request, String username, boolean success, String message) {
        SysLoginLogDO loginLog = new SysLoginLogDO();
        loginLog.setTenantId(TenantContext.getTenantId());
        loginLog.setUsername(username);
        loginLog.setIp(request.getRemoteAddr());
        String userAgent = request.getHeader("User-Agent");
        loginLog.setUserAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        loginLog.setStatus(success ? LOGIN_SUCCESS : LOGIN_FAILURE);
        loginLog.setMessage(message);
        loginLog.setLoginTime(LocalDateTime.now());
        loginLogMapper.insert(loginLog);
        if (success) {
            businessMetrics.loginSuccess();
        } else {
            businessMetrics.loginFailure();
        }
    }

    private boolean isRateLimited(String ip) {
        String key = LOGIN_RATE_KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(RATE_LIMIT_WINDOW_MINUTES));
        }
        return count != null && count > RATE_LIMIT_PER_MINUTE;
    }

    /**
     * 登录失败锁定检查。R4-1.39 键带租户维度：此前只按用户名（login:fail:&lt;username&gt;），
     * 匿名攻击者 5 次错密码即可锁定任意真实账号 10 分钟（账号级 DoS），且租户 A 被锁会
     * 连带锁掉租户 B 同名账号。未指定租户的失败归 "*" 桶，只影响未指定租户的登录探测。
     */
    private void checkLoginLockout(String username, Long tenantId) {
        String value = redisTemplate.opsForValue().get(failKey(username, tenantId));
        if (value != null && Integer.parseInt(value) >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
    }

    private void recordLoginFailure(String username, Long tenantId) {
        String key = failKey(username, tenantId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return;
        }
        // 达到阈值后每次继续失败按 2 的幂延长锁定（10→20→40→80 封顶）并刷新 TTL，
        // 防止攻击者卡在 10 分钟窗口末尾反复刷 5 次仅维持短锁。
        long lockMinutes = count <= MAX_LOGIN_FAILURES
                ? FAILURE_LOCK_MINUTES
                : Math.min(FAILURE_LOCK_MINUTES * (1L << (int) Math.min(count - MAX_LOGIN_FAILURES, 3L)), MAX_LOCK_MINUTES);
        redisTemplate.expire(key, Duration.ofMinutes(lockMinutes));
    }

    /** 失败锁定键：带租户维度，未指定租户归 "*" 桶，避免跨租户同名账号互锁。 */
    private static String failKey(String username, Long tenantId) {
        return LOGIN_FAIL_KEY_PREFIX + (tenantId == null ? "*" : tenantId) + ":" + username;
    }

    /** 登录成功清空失败计数：请求可能未指定租户，须同时清用户实际租户对应的键。 */
    private void clearLoginFailures(String username, Long requestedTenantId, Long actualTenantId) {
        redisTemplate.delete(failKey(username, requestedTenantId));
        redisTemplate.delete(failKey(username, actualTenantId));
    }

    private boolean captchaEnabled() {
        return loginConfig().isCaptchaEnabled();
    }

    /** JWT claim 中的 JSON 数字可能被解析为 Integer 或 Long，统一转 Long；null 返回 null。 */
    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
