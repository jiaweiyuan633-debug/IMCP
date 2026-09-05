package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SseTicketService;
import cn.admin.scaffold.security.SecurityUtils;
import cn.admin.scaffold.module.ai.dto.AiCallbackRequest;
import cn.admin.scaffold.module.ai.dto.AiConfigSaveRequest;
import cn.admin.scaffold.module.ai.dto.AiTaskCreateRequest;
import cn.admin.scaffold.module.ai.dto.AiTaskQuery;
import cn.admin.scaffold.module.ai.dto.AiTaskRetryRequest;
import cn.admin.scaffold.module.ai.vo.AiConfigVo;
import cn.admin.scaffold.module.ai.vo.AiTaskRetryResult;
import cn.admin.scaffold.module.ai.vo.AiTaskVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    /**
     * AI 回调请求体上限（字节）。回调语义为状态回传 + 结果摘要，不应携带大体积内容；
     * 原实现 {@code getInputStream().readAllBytes()} 无界整读，而 {@code /api/ai/callback/**}
     * 为 permitAll 且 HMAC 校验在整读之后——未认证者可 POST 任意巨大 body 触发 OOM。
     */
    private static final int MAX_CALLBACK_BODY = 1024 * 1024;

    private final AiConfigService configService;
    private final AiTaskService taskService;
    private final AiTaskStreamService taskStreamService;
    private final SseTicketService sseTicketService;
    private final ObjectMapper objectMapper;

    @GetMapping("/ticket")
    public Result<String> sseTicket() {
        return Result.success(sseTicketService.issue(SecurityUtils.getUserId()));
    }

    @GetMapping("/tasks/{id}/stream")
    public SseEmitter taskStream(@PathVariable Long id, @RequestParam String ticket) {
        Long userId = sseTicketService.consume(ticket);
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 票据即身份，透传 userId —— 连接时在请求线程完成访问校验与租户捕获
        // （AiTaskStreamService.stream → AiTaskService.openStream），轮询线程据此恢复上下文。
        return taskStreamService.stream(id, userId);
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('ai:config:list')")
    public Result<List<AiConfigVo>> listConfigs() {
        return Result.success(configService.list());
    }

    @PutMapping("/config/{id}")
    @PreAuthorize("hasAuthority('ai:config:edit')")
    @OperLog(module = "AI 管理", action = "编辑 AI 服务配置")
    public Result<Void> updateConfig(@PathVariable Long id, @Valid @RequestBody AiConfigSaveRequest request) {
        request.setId(id);
        configService.update(request);
        return Result.success();
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('ai:task:create')")
    @OperLog(module = "AI 管理", action = "创建 AI 任务")
    public Result<Long> createTask(@Valid @RequestBody AiTaskCreateRequest request) {
        return Result.success(taskService.create(request));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('ai:task:list')")
    public Result<PageResult<AiTaskVo>> pageTasks(AiTaskQuery query) {
        return Result.success(taskService.page(query));
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('ai:task:list')")
    public Result<AiTaskVo> taskDetail(@PathVariable Long id) {
        return Result.success(taskService.detail(id));
    }

    @DeleteMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('ai:task:cancel')")
    @OperLog(module = "AI 管理", action = "取消 AI 任务")
    public Result<Void> cancelTask(@PathVariable Long id) {
        taskService.cancel(id);
        return Result.success();
    }

    @PostMapping("/tasks/retry")
    @PreAuthorize("hasAuthority('ai:task:retry')")
    @OperLog(module = "AI 管理", action = "重试 AI 任务")
    public Result<AiTaskRetryResult> retryTasks(@Valid @RequestBody AiTaskRetryRequest request) {
        return Result.success(taskService.retry(request.getIds()));
    }

    @PostMapping("/callback/task")
    public Result<Void> callback(
            HttpServletRequest servletRequest,
            @RequestHeader("X-Ai-Timestamp") String timestamp,
            @RequestHeader("X-Ai-Signature") String signature) throws IOException {
        // 读取原始请求体用于 HMAC 校验（校验内容与 AI 侧签名字节必须一致），再反序列化业务 DTO。
        // readNBytes(MAX+1) 有界读：超限即拒，避免 permitAll 端点被巨型 body 打 OOM
        byte[] body = servletRequest.getInputStream().readNBytes(MAX_CALLBACK_BODY + 1);
        if (body.length > MAX_CALLBACK_BODY) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "AI 回调请求体过大");
        }
        AiCallbackRequest request = objectMapper.readValue(body, AiCallbackRequest.class);
        taskService.handleCallback(request, body, timestamp, signature);
        return Result.success();
    }
}

