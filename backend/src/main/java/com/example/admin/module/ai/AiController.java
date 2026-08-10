package com.example.admin.module.ai;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.SseTicketService;
import com.example.admin.security.SecurityUtils;
import com.example.admin.module.ai.dto.AiCallbackRequest;
import com.example.admin.module.ai.dto.AiConfigSaveRequest;
import com.example.admin.module.ai.dto.AiTaskCreateRequest;
import com.example.admin.module.ai.dto.AiTaskQuery;
import com.example.admin.module.ai.vo.AiConfigVo;
import com.example.admin.module.ai.vo.AiTaskVo;
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
        return taskStreamService.stream(id);
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

    @PostMapping("/callback/task")
    public Result<Void> callback(
            HttpServletRequest servletRequest,
            @RequestHeader("X-Ai-Timestamp") String timestamp,
            @RequestHeader("X-Ai-Signature") String signature) throws IOException {
        // 读取原始请求体用于 HMAC 校验（校验内容与 AI 侧签名字节必须一致），再反序列化业务 DTO
        byte[] body = servletRequest.getInputStream().readAllBytes();
        AiCallbackRequest request = objectMapper.readValue(body, AiCallbackRequest.class);
        taskService.handleCallback(request, body, timestamp, signature);
        return Result.success();
    }
}

