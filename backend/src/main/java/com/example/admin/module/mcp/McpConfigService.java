package com.example.admin.module.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.mcp.dto.McpServerQuery;
import com.example.admin.module.mcp.dto.McpServerSaveRequest;
import com.example.admin.module.mcp.entity.SysMcpServerDO;
import com.example.admin.module.mcp.mapper.SysMcpServerMapper;
import com.example.admin.module.mcp.vo.McpServerVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 外部 MCP Server 配置服务：管理本平台作为 MCP Client 时接入的外部服务。
 */
@Service
@RequiredArgsConstructor
public class McpConfigService {

    private static final int ENABLED = 1;

    private final SysMcpServerMapper mcpServerMapper;

    public PageResult<McpServerVo> page(McpServerQuery query) {
        Page<SysMcpServerDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysMcpServerDO> wrapper = new LambdaQueryWrapper<SysMcpServerDO>()
                .like(StringUtils.hasText(query.getKeyword()), SysMcpServerDO::getName, query.getKeyword())
                .eq(query.getEnabled() != null, SysMcpServerDO::getEnabled, query.getEnabled())
                .orderByAsc(SysMcpServerDO::getSort)
                .orderByAsc(SysMcpServerDO::getId);
        IPage<SysMcpServerDO> result = mcpServerMapper.selectPage(page, wrapper);
        List<McpServerVo> records = result.getRecords().stream().map(c -> toVo(c)).toList();
        return PageResult.of(result, records);
    }

    public Long create(McpServerSaveRequest request) {
        SysMcpServerDO server = toEntity(request);
        mcpServerMapper.insert(server);
        return server.getId();
    }

    public void update(McpServerSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "服务 ID 不能为空");
        }
        mcpServerMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer enabled) {
        SysMcpServerDO server = new SysMcpServerDO();
        server.setId(id);
        server.setEnabled(enabled);
        mcpServerMapper.updateById(server);
    }

    public void delete(Long id) {
        mcpServerMapper.deleteById(id);
    }

    /** 加载启用的服务配置；停用或不存在则报错。 */
    public SysMcpServerDO requireEnabled(Long id) {
        SysMcpServerDO server = mcpServerMapper.selectById(id);
        if (server == null || server.getEnabled() == null || server.getEnabled() != ENABLED) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "MCP 服务不存在或未启用");
        }
        return server;
    }

    private SysMcpServerDO toEntity(McpServerSaveRequest request) {
        SysMcpServerDO server = new SysMcpServerDO();
        server.setId(request.getId());
        server.setName(request.getName());
        server.setUrl(request.getUrl());
        server.setAuthToken(request.getAuthToken());
        server.setEnabled(request.getEnabled() == null ? ENABLED : request.getEnabled());
        server.setSort(request.getSort() == null ? 0 : request.getSort());
        server.setRemark(request.getRemark());
        return server;
    }

    private McpServerVo toVo(SysMcpServerDO server) {
        return McpServerVo.builder()
                .id(server.getId())
                .name(server.getName())
                .url(server.getUrl())
                .authToken(server.getAuthToken())
                .enabled(server.getEnabled())
                .sort(server.getSort())
                .remark(server.getRemark())
                .createdAt(server.getCreatedAt())
                .build();
    }
}
