package cn.admin.scaffold.module.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.common.SsrfUrlValidator;
import cn.admin.scaffold.module.mcp.dto.McpServerQuery;
import cn.admin.scaffold.module.mcp.dto.McpServerSaveRequest;
import cn.admin.scaffold.module.mcp.entity.SysMcpServerDO;
import cn.admin.scaffold.module.mcp.mapper.SysMcpServerMapper;
import cn.admin.scaffold.module.mcp.vo.McpServerVo;
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
    private final SecretCipher secretCipher;

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
        validateUrl(request.getUrl());
        SysMcpServerDO server = toEntity(request);
        mcpServerMapper.insert(server);
        return server.getId();
    }

    public void update(McpServerSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "服务 ID 不能为空");
        }
        validateUrl(request.getUrl());
        SysMcpServerDO server = toEntity(request);
        // 令牌不回显给前端，编辑留空表示不修改：从库中保留原令牌，避免空串覆盖
        if (!StringUtils.hasText(request.getAuthToken())) {
            SysMcpServerDO existing = mcpServerMapper.selectById(request.getId());
            if (existing != null) {
                server.setAuthToken(existing.getAuthToken());
            }
        }
        mcpServerMapper.updateById(server);
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
        server.setAuthToken(encryptToken(request.getAuthToken()));
        server.setEnabled(request.getEnabled() == null ? Integer.valueOf(ENABLED) : request.getEnabled());
        server.setSort(request.getSort() == null ? Integer.valueOf(0) : request.getSort());
        server.setRemark(request.getRemark());
        return server;
    }

    /** authToken 落库加密（SecretCipher，"enc:" 前缀幂等跳过）——明文落库时数据库泄露即第三方凭据泄露。 */
    private String encryptToken(String token) {
        if (!StringUtils.hasText(token) || secretCipher.isEncrypted(token)) {
            return token;
        }
        return secretCipher.encrypt(token);
    }

    /**
     * 保存时静态 SSRF 校验（协议/主机/IP 字面量，不发 DNS）——外部 MCP 地址若指向
     * 内网/云元数据，平台服务端作为跳板可探测内网。投递时 McpClientService 还会做 DNS 复核。
     */
    private void validateUrl(String url) {
        String error = SsrfUrlValidator.validateOutboundHttpUrl(url);
        if (error != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "MCP Server 地址不合法：" + error);
        }
    }

    private McpServerVo toVo(SysMcpServerDO server) {
        return McpServerVo.builder()
                .id(server.getId())
                .name(server.getName())
                .url(server.getUrl())
                .hasAuthToken(StringUtils.hasText(server.getAuthToken()))
                .enabled(server.getEnabled())
                .sort(server.getSort())
                .remark(server.getRemark())
                .createdAt(server.getCreatedAt())
                .build();
    }
}
