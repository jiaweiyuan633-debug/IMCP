package com.example.admin.module.system;

import com.example.admin.common.PageResult;
import com.example.admin.common.Result;
import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.entity.SysNotice;
import com.example.admin.security.SecurityUtils;
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

import java.util.List;

@RestController
@RequestMapping("/api/system/notice")
@RequiredArgsConstructor
public class SystemNoticeController {

    private final SystemNoticeService noticeService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:notice:list')")
    public Result<PageResult<SysNotice>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer type) {
        return Result.success(noticeService.page(pageNum, pageSize, title, type));
    }

    @GetMapping("/latest")
    public Result<List<SysNotice>> latest(@RequestParam(defaultValue = "5") int limit) {
        return Result.success(noticeService.latest(limit));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:add')")
    @OperLog(module = "通知公告", action = "新增公告")
    public Result<Long> create(@RequestBody SysNotice notice) {
        return Result.success(noticeService.create(notice));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:notice:edit')")
    @OperLog(module = "通知公告", action = "编辑公告")
    public Result<Void> update(@RequestBody SysNotice notice) {
        noticeService.update(notice);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:delete')")
    @OperLog(module = "通知公告", action = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success();
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(noticeService.unreadCount(SecurityUtils.getUserId()));
    }

    @PutMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable Long id) {
        noticeService.markRead(SecurityUtils.getUserId(), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        noticeService.markAllRead(SecurityUtils.getUserId());
        return Result.success();
    }
}

