package cn.admin.scaffold.module.mcp;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.mcp.dto.McpCallToolRequest;
import cn.admin.scaffold.module.mcp.dto.McpServerQuery;
import cn.admin.scaffold.module.mcp.dto.McpServerSaveRequest;
import cn.admin.scaffold.module.mcp.vo.McpCallResultVo;
import cn.admin.scaffold.module.mcp.vo.McpServerVo;
import cn.admin.scaffold.module.mcp.vo.McpToolVo;
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

import java.util.List;
import java.util.Map;

/**
 * MCP 控制器：外部 MCP Server 配置管理 + 工具浏览与调用。
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpConfigService configService;
    private final McpClientService clientService;

    @GetMapping("/server")
    @PreAuthorize("hasAuthority('system:mcp:list')")
    public Result<PageResult<McpServerVo>> page(McpServerQuery query) {
        return Result.success(configService.page(query));
    }

    @PostMapping("/server")
    @PreAuthorize("hasAuthority('system:mcp:add')")
    @OperLog(module = "MCP 服务", action = "新增服务")
    public Result<Long> create(@Valid @RequestBody McpServerSaveRequest request) {
        return Result.success(configService.create(request));
    }

    @PutMapping("/server")
    @PreAuthorize("hasAuthority('system:mcp:edit')")
    @OperLog(module = "MCP 服务", action = "编辑服务")
    public Result<Void> update(@Valid @RequestBody McpServerSaveRequest request) {
        configService.update(request);
        clientService.evict(request.getId());
        return Result.success();
    }

    @PutMapping("/server/{id}/status")
    @PreAuthorize("hasAuthority('system:mcp:status')")
    @OperLog(module = "MCP 服务", action = "修改服务状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        configService.updateStatus(id, body.get("enabled"));
        clientService.evict(id);
        return Result.success();
    }

    @DeleteMapping("/server/{id}")
    @PreAuthorize("hasAuthority('system:mcp:delete')")
    @OperLog(module = "MCP 服务", action = "删除服务")
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        clientService.evict(id);
        return Result.success();
    }

    /** 浏览外部服务可用工具（同时测试连通性）。 */
    @GetMapping("/server/{id}/tools")
    @PreAuthorize("hasAuthority('system:mcp:list')")
    public Result<List<McpToolVo>> tools(@PathVariable Long id) {
        return Result.success(clientService.listTools(id));
    }

    /** 调用外部工具。 */
    @PostMapping("/server/{id}/call")
    @PreAuthorize("hasAuthority('system:mcp:list')")
    @OperLog(module = "MCP 服务", action = "调用外部工具")
    public Result<McpCallResultVo> call(@PathVariable Long id, @Valid @RequestBody McpCallToolRequest request) {
        return Result.success(clientService.callTool(id, request.getToolName(), request.getArguments()));
    }
}
