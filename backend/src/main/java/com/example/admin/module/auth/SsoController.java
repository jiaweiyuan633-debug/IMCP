package com.example.admin.module.auth;

import com.example.admin.common.Result;
import com.example.admin.module.auth.dto.SsoTokenRequest;
import com.example.admin.module.auth.vo.SsoAuthorizeVo;
import com.example.admin.module.auth.vo.SsoTokenVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 授权服务控制器：授权码签发与令牌交换。
 */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class SsoController {

    private final SsoAuthService ssoAuthService;

    /** 第三方应用用授权码换访问令牌（公开端点）。 */
    @PostMapping("/token")
    public Result<SsoTokenVo> token(@Valid @RequestBody SsoTokenRequest request) {
        return Result.success(ssoAuthService.token(request));
    }

    /** 当前登录用户授权第三方应用（需登录）。 */
    @GetMapping("/authorize")
    public Result<SsoAuthorizeVo> authorize(@RequestParam("clientId") String clientId,
                                            @RequestParam(value = "redirectUri", required = false) String redirectUri) {
        return Result.success(ssoAuthService.authorize(clientId, redirectUri));
    }
}
