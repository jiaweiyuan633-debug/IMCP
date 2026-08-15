package com.example.admin.module.auth;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.auth.dto.OauthAuthorizeUrlRequest;
import com.example.admin.module.auth.dto.OauthBindRequest;
import com.example.admin.module.auth.dto.OauthClientQuery;
import com.example.admin.module.auth.dto.OauthClientSaveRequest;
import com.example.admin.module.auth.dto.OauthConfigQuery;
import com.example.admin.module.auth.dto.OauthConfigSaveRequest;
import com.example.admin.module.auth.dto.OauthStatusRequest;
import com.example.admin.module.auth.vo.LoginResponse;
import com.example.admin.module.auth.vo.OauthBindingVo;
import com.example.admin.module.auth.vo.OauthClientVo;
import com.example.admin.module.auth.vo.OauthConfigVo;
import com.example.admin.module.auth.vo.OauthProviderVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 第三方 OAuth2 控制器：登录回调 + 第三方登录配置 + SSO 应用管理。
 */
@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OauthController {

    private final OauthLoginService oauthLoginService;
    private final OauthConfigService oauthConfigService;
    private final OauthClientService oauthClientService;

    // ---------- 第三方登录 ----------

    @GetMapping("/providers")
    public Result<List<OauthProviderVo>> providers() {
        return Result.success(oauthLoginService.providers());
    }

    @PostMapping("/authorize-url")
    public Result<Map<String, String>> authorizeUrl(@Valid @RequestBody OauthAuthorizeUrlRequest request) {
        return Result.success(Map.of("url", oauthLoginService.buildAuthorizeUrl(request)));
    }

    /** 第三方回调：处理完成后 302 到前端 /oauth/callback。 */
    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable("provider") String provider,
                         @RequestParam("code") String code,
                         @RequestParam("state") String state,
                         HttpServletRequest httpRequest,
                         HttpServletResponse response) throws IOException {
        response.sendRedirect(oauthLoginService.callback(provider, code, state, httpRequest));
    }

    /** 消费一次性登录 ticket。 */
    @PostMapping("/ticket")
    public Result<LoginResponse> ticket(@RequestBody Map<String, String> body) {
        return Result.success(oauthLoginService.consumeLoginTicket(body.get("ticket")));
    }

    @PostMapping("/bind")
    public Result<LoginResponse> bind(@Valid @RequestBody OauthBindRequest request, HttpServletRequest httpRequest) {
        return Result.success(oauthLoginService.bind(request, httpRequest));
    }

    @GetMapping("/bindings")
    public Result<List<OauthBindingVo>> bindings() {
        return Result.success(oauthLoginService.bindings());
    }

    @PostMapping("/unbind/{provider}")
    public Result<Void> unbind(@PathVariable("provider") String provider) {
        oauthLoginService.unbind(provider);
        return Result.success();
    }

    // ---------- 第三方登录配置管理 ----------

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('system:oauth:list')")
    public Result<PageResult<OauthConfigVo>> configPage(OauthConfigQuery query) {
        return Result.success(oauthConfigService.page(query));
    }

    @PostMapping("/config")
    @PreAuthorize("hasAuthority('system:oauth:add')")
    @OperLog(module = "第三方登录", action = "新增登录配置")
    public Result<Long> createConfig(@Valid @RequestBody OauthConfigSaveRequest request) {
        return Result.success(oauthConfigService.create(request));
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('system:oauth:edit')")
    @OperLog(module = "第三方登录", action = "编辑登录配置")
    public Result<Void> updateConfig(@Valid @RequestBody OauthConfigSaveRequest request) {
        oauthConfigService.update(request);
        return Result.success();
    }

    @PutMapping("/config/{id}/status")
    @PreAuthorize("hasAuthority('system:oauth:status')")
    @OperLog(module = "第三方登录", action = "修改登录配置状态")
    public Result<Void> updateConfigStatus(@PathVariable Long id, @Valid @RequestBody OauthStatusRequest request) {
        oauthConfigService.updateStatus(id, request.getEnabled());
        return Result.success();
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("hasAuthority('system:oauth:delete')")
    @OperLog(module = "第三方登录", action = "删除登录配置")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        oauthConfigService.delete(id);
        return Result.success();
    }

    // ---------- SSO 应用管理 ----------

    @GetMapping("/client")
    @PreAuthorize("hasAuthority('system:oauth:client:list')")
    public Result<PageResult<OauthClientVo>> clientPage(OauthClientQuery query) {
        return Result.success(oauthClientService.page(query));
    }

    @PostMapping("/client")
    @PreAuthorize("hasAuthority('system:oauth:client:add')")
    @OperLog(module = "SSO 应用", action = "新增应用")
    public Result<Long> createClient(@Valid @RequestBody OauthClientSaveRequest request) {
        return Result.success(oauthClientService.create(request));
    }

    @PutMapping("/client")
    @PreAuthorize("hasAuthority('system:oauth:client:edit')")
    @OperLog(module = "SSO 应用", action = "编辑应用")
    public Result<Void> updateClient(@Valid @RequestBody OauthClientSaveRequest request) {
        oauthClientService.update(request);
        return Result.success();
    }

    @PutMapping("/client/{id}/status")
    @PreAuthorize("hasAuthority('system:oauth:client:status')")
    @OperLog(module = "SSO 应用", action = "修改应用状态")
    public Result<Void> updateClientStatus(@PathVariable Long id, @Valid @RequestBody OauthStatusRequest request) {
        oauthClientService.updateStatus(id, request.getEnabled());
        return Result.success();
    }

    @DeleteMapping("/client/{id}")
    @PreAuthorize("hasAuthority('system:oauth:client:delete')")
    @OperLog(module = "SSO 应用", action = "删除应用")
    public Result<Void> deleteClient(@PathVariable Long id) {
        oauthClientService.delete(id);
        return Result.success();
    }
}
