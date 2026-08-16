package com.example.admin.module.notice;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.notice.dto.MessageTemplateQuery;
import com.example.admin.module.notice.dto.MessageTemplateSaveRequest;
import com.example.admin.module.notice.dto.MessageTemplateSendRequest;
import com.example.admin.module.notice.vo.MessageTemplateVo;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notice/message-template")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateService templateService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:message:template:list')")
    public Result<PageResult<MessageTemplateVo>> page(MessageTemplateQuery query) {
        return Result.success(templateService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:message:template:add')")
    @OperLog(module = "消息模板", action = "新增消息模板")
    public Result<Long> create(@Valid @RequestBody MessageTemplateSaveRequest request) {
        return Result.success(templateService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:message:template:edit')")
    @OperLog(module = "消息模板", action = "编辑消息模板")
    public Result<Void> update(@Valid @RequestBody MessageTemplateSaveRequest request) {
        templateService.update(request);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:message:template:status')")
    @OperLog(module = "消息模板", action = "修改消息模板状态")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody MessageTemplateSaveRequest request) {
        templateService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:message:template:delete')")
    @OperLog(module = "消息模板", action = "删除消息模板")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('system:message:template:send')")
    @OperLog(module = "消息模板", action = "按模板发送消息", maskFields = {"params"})
    public Result<Long> send(@Valid @RequestBody MessageTemplateSendRequest request) {
        return Result.success(templateService.sendByTemplate(request));
    }
}
