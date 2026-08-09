package com.example.admin.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.auth.dto.ChangePasswordRequest;
import com.example.admin.module.auth.dto.LoginRequest;
import com.example.admin.module.auth.dto.ProfileUpdateRequest;
import com.example.admin.module.auth.dto.RefreshRequest;
import com.example.admin.module.auth.dto.TotpCodeRequest;
import com.example.admin.module.auth.vo.LoginResponse;
import com.example.admin.module.auth.vo.LoginConfigVo;
import com.example.admin.module.auth.vo.TotpStatusVo;
import com.example.admin.module.auth.vo.UserInfoVo;
import com.example.admin.module.monitor.vo.OnlineUserVo;
import com.example.admin.module.system.entity.SysLoginLogDO;
import com.example.admin.module.system.entity.SysConfigDO;
import com.example.admin.module.system.entity.SysMenuDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.mapper.SysLoginLogMapper;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.vo.MenuVo;
import com.example.admin.common.TenantContext;
import com.example.admin.security.JwtUtil;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import com.example.admin.security.TokenService;
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

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CAPTCHA_CONFIG_KEY = "sys.account.captchaEnabled";
    private static final String LOGIN_RATE_KEY_PREFIX = "login:rate:";
    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";
    private static final int RATE_LIMIT_PER_MINUTE = 20;
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final long RATE_LIMIT_WINDOW_MINUTES = 1;
    private static final long FAILURE_LOCK_MINUTES = 10;
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

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (isRateLimited(ip)) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
        checkLoginLockout(request.getUsername());
        if (captchaEnabled() && !captchaService.verify(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }
        String username = request.getUsername().trim();
        SysUserDO user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getUsername, username));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            saveLoginLog(httpRequest, username, false, "用户名或密码错误");
            recordLoginFailure(username);
            throw new BusinessException(ResultCode.BAD_CREDENTIALS);
        }
        TenantContext.setTenantId(user.getTenantId());
        if (user.getStatus() == null || user.getStatus() != 1) {
            saveLoginLog(httpRequest, username, false, "账号已被禁用");
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (user.getTotpEnabled() != null && user.getTotpEnabled() == 1
                && !totpService.verify(totpService.decrypt(user.getTotpSecret()), request.getTotpCode())) {
            throw new BusinessException(ResultCode.TOTP_REQUIRED);
        }
        redisTemplate.delete(LOGIN_FAIL_KEY_PREFIX + username);

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        List<String> perms = menuMapper.selectPermsByUserId(user.getId());
        List<MenuVo> menus = buildMenuTree(menuMapper.selectMenusByUserId(user.getId()));
        String accessJti = jwtUtil.generateJti();
        String refreshJti = jwtUtil.generateJti();

        String accessToken = jwtUtil.createAccessToken(accessJti, user.getId(), user.getUsername(), roles, perms);
        String refreshToken = jwtUtil.createRefreshToken(refreshJti, user.getId(), user.getUsername());
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
        saveLoginLog(httpRequest, username, true, "登录成功");

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserInfo(user, roles, perms, menus))
                .build();
    }

    public LoginConfigVo loginConfig() {
        SysConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, CAPTCHA_CONFIG_KEY));
        return LoginConfigVo.builder()
                .captchaEnabled(config != null && Boolean.parseBoolean(config.getConfigValue()))
                .build();
    }

    public LoginResponse refresh(RefreshRequest request) {
        Claims claims = jwtUtil.parse(request.getRefreshToken());
        String refreshJti = claims.getId();
        if (!tokenService.hasRefreshToken(refreshJti)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Long userId = Long.valueOf(claims.getSubject());
        SysUserDO user = userMapper.selectById(userId);
        if (user != null && user.getTenantId() != null) {
            TenantContext.setTenantId(user.getTenantId());
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        tokenService.revokeRefreshToken(refreshJti);
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        List<String> perms = menuMapper.selectPermsByUserId(userId);
        List<MenuVo> menus = buildMenuTree(menuMapper.selectMenusByUserId(userId));
        String accessJti = jwtUtil.generateJti();
        String newRefreshJti = jwtUtil.generateJti();

        String accessToken = jwtUtil.createAccessToken(accessJti, userId, user.getUsername(), roles, perms);
        String refreshToken = jwtUtil.createRefreshToken(newRefreshJti, userId, user.getUsername());
        tokenService.saveAccessToken(accessJti, newRefreshJti);
        tokenService.saveRefreshToken(newRefreshJti, String.valueOf(userId));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(toUserInfo(user, roles, perms, menus))
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
                .build();
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
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
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
    }

    private boolean isRateLimited(String ip) {
        String key = LOGIN_RATE_KEY_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(RATE_LIMIT_WINDOW_MINUTES));
        }
        return count != null && count > RATE_LIMIT_PER_MINUTE;
    }

    private void checkLoginLockout(String username) {
        String value = redisTemplate.opsForValue().get(LOGIN_FAIL_KEY_PREFIX + username);
        if (value != null && Integer.parseInt(value) >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(ResultCode.LOGIN_TOO_MANY);
        }
    }

    private void recordLoginFailure(String username) {
        String key = LOGIN_FAIL_KEY_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(FAILURE_LOCK_MINUTES));
        }
    }

    private boolean captchaEnabled() {
        return loginConfig().isCaptchaEnabled();
    }
}
