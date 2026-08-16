package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.dto.MessageSendRequest;
import com.example.admin.module.system.entity.SysMessageDO;
import com.example.admin.module.system.entity.SysNoticeDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
import com.example.admin.module.system.vo.NotificationFeedItemVO;
import com.example.admin.module.system.warmflow.WarmFlowWorkflowService;
import com.example.admin.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/system/message")
@RequiredArgsConstructor
public class SystemMessageController {

    private final SystemMessageService messageService;
    private final SystemNoticeService noticeService;
    private final WarmFlowWorkflowService workflowService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<SysMessageDO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) Integer readStatus) {
        return Result.success(messageService.page(pageNum, pageSize, messageType, readStatus));
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysMessageDO>> latest(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(messageService.latest(limit));
    }

    @GetMapping("/feed")
    @PreAuthorize("isAuthenticated()")
    public Result<List<NotificationFeedItemVO>> feed(@RequestParam(defaultValue = "8") int limit) {
        int capped = Math.min(Math.max(limit, 1), 20);
        List<NotificationFeedItemVO> items = new ArrayList<>();
        for (SysMessageDO message : messageService.latest(capped)) {
            NotificationFeedItemVO item = new NotificationFeedItemVO();
            item.setKind("message");
            item.setId(message.getId());
            item.setTitle(message.getTitle());
            item.setContent(message.getContent());
            item.setBizType(message.getBizType());
            item.setBizId(message.getBizId());
            item.setCreatedAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
            item.setTag(null);
            items.add(item);
        }
        for (SysNoticeDO notice : noticeService.latest(capped)) {
            NotificationFeedItemVO item = new NotificationFeedItemVO();
            item.setKind("notice");
            item.setId(notice.getId());
            item.setTitle(notice.getNoticeTitle());
            item.setContent(notice.getNoticeContent());
            item.setCreatedAt(notice.getCreatedAt() == null ? null : notice.getCreatedAt().toString());
            item.setTag(null);
            items.add(item);
        }
        items.sort((a, b) -> String.valueOf(b.getCreatedAt()).compareTo(String.valueOf(a.getCreatedAt())));
        return Result.success(items.stream().limit(capped).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<SysMessageDO> detail(@PathVariable Long id) {
        return Result.success(messageService.detail(SecurityUtils.getUserId(), id));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> unreadCount() {
        return Result.success(messageService.unreadCount(SecurityUtils.getUserId()));
    }

    @GetMapping("/todos")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<SysWorkflowDO>> todos(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(workflowService.taskPage(pageNum, pageSize, null));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:message:add')")
    @OperLog(module = "消息中心", action = "发送消息", maskFields = {"content"})
    public Result<Long> send(@Valid @RequestBody MessageSendRequest request) {
        Long senderId = SecurityUtils.getUserId();
        return Result.success(messageService.send(
                senderId, "SYSTEM", request.getTitle(), request.getContent(),
                null, null, request.getReceiverIds()));
    }

    @PutMapping("/read/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(SecurityUtils.getUserId(), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAllRead() {
        messageService.markAllRead(SecurityUtils.getUserId());
        return Result.success();
    }
}
