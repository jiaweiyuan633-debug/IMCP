package cn.admin.scaffold.module.auth;

import cn.admin.scaffold.common.RefreshTokenCookieSupport;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.module.auth.vo.CaptchaResponse;
import cn.admin.scaffold.module.auth.vo.LoginConfigVo;
import cn.admin.scaffold.module.auth.dto.ChangePasswordRequest;
import cn.admin.scaffold.module.auth.dto.LoginRequest;
import cn.admin.scaffold.module.auth.dto.ProfileUpdateRequest;
import cn.admin.scaffold.module.auth.dto.RefreshRequest;
import cn.admin.scaffold.module.auth.dto.TotpCodeRequest;
import cn.admin.scaffold.module.auth.vo.LoginResponse;
import cn.admin.scaffold.module.auth.vo.UserInfoVo;
import cn.admin.scaffold.module.auth.vo.TotpStatusVo;
import cn.admin.scaffold.security.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "认证", description = "登录/登出/Token 刷新/个人中心/两步验证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final RefreshTokenCookieSupport refreshTokenCookieSupport;
    private final JwtProperties jwtProperties;

    @GetMapping("/login-config")
    public Result<LoginConfigVo> loginConfig() {
        return Result.success(authService.loginConfig());
    }

    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() throws Exception {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request, httpRequest);
        // P1-F3：refresh token 迁移 httpOnly Cookie（XSS 无法读取），响应体字段保留供兼容
        refreshTokenCookieSupport.setRefreshCookie(httpResponse, response.getRefreshToken(),
                jwtProperties.getRefreshTokenExpireDays() * 24 * 60 * 60L);
        return Result.success(response);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        // P1-F3：优先从 httpOnly Cookie 读取 refresh token；无 cookie 时回退请求体（兼容旧客户端/脚本）
        String refreshToken = refreshTokenCookieSupport.readRefreshCookie(httpRequest);
        if (refreshToken == null && request != null && request.getRefreshToken() != null) {
            refreshToken = request.getRefreshToken();
        }
        LoginResponse response = authService.refresh(refreshToken);
        // 轮换后的新 refresh token 写回 cookie
        refreshTokenCookieSupport.setRefreshCookie(httpResponse, response.getRefreshToken(),
                jwtProperties.getRefreshTokenExpireDays() * 24 * 60 * 60L);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse httpResponse) {
        authService.logout(request);
        refreshTokenCookieSupport.clearRefreshCookie(httpResponse);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserInfoVo> me() {
        return Result.success(authService.me());
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.success();
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        authService.updateProfile(request);
        return Result.success();
    }

    @GetMapping("/totp/status")
    public Result<TotpStatusVo> totpStatus() {
        return Result.success(authService.totpStatus());
    }

    @PostMapping("/totp/setup")
    public Result<TotpStatusVo> setupTotp() {
        return Result.success(authService.setupTotp());
    }

    @PostMapping("/totp/enable")
    public Result<Void> enableTotp(@Valid @RequestBody TotpCodeRequest request) {
        authService.enableTotp(request);
        return Result.success();
    }

    @PostMapping("/totp/disable")
    public Result<Void> disableTotp(@Valid @RequestBody TotpCodeRequest request) {
        authService.disableTotp(request);
        return Result.success();
    }
}
