package cn.admin.scaffold.module.notice;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.notice.dto.ChannelConfigQuery;
import cn.admin.scaffold.module.notice.dto.ChannelConfigSaveRequest;
import cn.admin.scaffold.module.notice.dto.ChannelSendRequest;
import cn.admin.scaffold.module.notice.dto.ChannelStatusRequest;
import cn.admin.scaffold.module.notice.vo.ChannelConfigVo;
import cn.admin.scaffold.module.notice.vo.ChannelLogVo;
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
@RequestMapping("/api/notice/channel")
@RequiredArgsConstructor
public class ChannelConfigController {

    private final ChannelConfigService channelConfigService;

    @GetMapping
    @PreAuthorize("hasAuthority('notice:channel:list')")
    public Result<PageResult<ChannelConfigVo>> page(ChannelConfigQuery query) {
        return Result.success(channelConfigService.page(query));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('notice:channel:add')")
    @OperLog(module = "消息渠道", action = "新增渠道配置")
    public Result<Long> create(@Valid @RequestBody ChannelConfigSaveRequest request) {
        return Result.success(channelConfigService.create(request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('notice:channel:edit')")
    @OperLog(module = "消息渠道", action = "编辑渠道配置")
    public Result<Void> update(@Valid @RequestBody ChannelConfigSaveRequest request) {
        channelConfigService.update(request);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('notice:channel:status')")
    @OperLog(module = "消息渠道", action = "修改渠道状态")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ChannelStatusRequest request) {
        channelConfigService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('notice:channel:delete')")
    @OperLog(module = "消息渠道", action = "删除渠道配置")
    public Result<Void> delete(@PathVariable Long id) {
        channelConfigService.delete(id);
        return Result.success();
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('notice:channel:send')")
    @OperLog(module = "消息渠道", action = "发送渠道消息", maskFields = {"content", "target"})
    public Result<Long> send(@Valid @RequestBody ChannelSendRequest request) {
        return Result.success(channelConfigService.send(request));
    }

    @GetMapping("/log")
    @PreAuthorize("hasAuthority('notice:channel:log')")
    public Result<PageResult<ChannelLogVo>> logPage(ChannelConfigQuery query) {
        return Result.success(channelConfigService.logPage(query));
    }
}
