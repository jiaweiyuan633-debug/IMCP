package cn.admin.scaffold.module.mcp;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.common.SsrfUrlValidator;
import cn.admin.scaffold.module.mcp.entity.SysMcpServerDO;
import cn.admin.scaffold.module.mcp.vo.McpCallResultVo;
import cn.admin.scaffold.module.mcp.vo.McpToolVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部 MCP Client 服务：连接并调用外部 MCP Server（SSE 传输），
 * 连接按服务 ID 缓存，停用或删除后仍保留缓存由下次调用重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientService {

    private final McpConfigService configService;
    private final ObjectMapper objectMapper;
    private final SecretCipher secretCipher;

    private final Map<Long, McpSyncClient> clients = new ConcurrentHashMap<>();

    /** 列出外部服务可用工具（同时充当连通性测试）。 */
    public List<McpToolVo> listTools(Long serverId) {
        McpSyncClient client = client(serverId);
        try {
            return client.listTools().tools().stream()
                    .map(t -> McpToolVo.builder()
                            .name(t.name())
                            .title(t.title())
                            .description(t.description())
                            .build())
                    .toList();
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "连接 MCP 服务失败：" + exception.getMessage());
        }
    }

    /** 调用外部工具。 */
    public McpCallResultVo callTool(Long serverId, String toolName, Map<String, Object> arguments) {
        McpSyncClient client = client(serverId);
        try {
            Map<String, Object> args = arguments == null ? Map.of() : arguments;
            CallToolResult result = client.callTool(new CallToolRequest(toolName, args));
            List<String> content = result.content() == null ? List.of()
                    : result.content().stream().map(c -> c instanceof TextContent text ? text.text() : c.toString()).toList();
            return McpCallResultVo.builder()
                    .isError(Boolean.TRUE.equals(result.isError()))
                    .content(content)
                    .structuredContent(result.structuredContent())
                    .build();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "调用工具失败：" + exception.getMessage());
        }
    }

    /** 清除服务缓存连接（配置变更后调用）。 */
    public void evict(Long serverId) {
        McpSyncClient client = clients.remove(serverId);
        if (client != null) {
            closeQuietly(client);
        }
    }

    private McpSyncClient client(Long serverId) {
        return clients.computeIfAbsent(serverId, id -> connect(configService.requireEnabled(id)));
    }

    private McpSyncClient connect(SysMcpServerDO server) {
        String url = server.getUrl();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "MCP Server 地址不能为空");
        }
        // R4-1.40：投递时 SSRF 复核——保存时静态校验兜不住"主机名解析到内网 IP"与"保存后 DNS 变更"，
        // 连接前按解析后的全部地址复核，任一落在内网/保留网段即拒绝
        String ssrfError = SsrfUrlValidator.validateOutboundHttpUrlWithDns(url);
        if (ssrfError != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "MCP Server 地址存在 SSRF 风险：" + ssrfError);
        }
        // R4-1.40：authToken 落库为 SecretCipher 密文，连接前解密为明文放入 Authorization 头；
        // 存量明文 decrypt 原样放行（兼容），解密失败返回 null（无令牌请求）
        String authToken = secretCipher.decrypt(server.getAuthToken());
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(url)
                .customizeClient(builder -> builder.connectTimeout(Duration.ofSeconds(5)))
                .customizeRequest(builder -> {
                    if (StringUtils.hasText(authToken)) {
                        builder.header("Authorization", "Bearer " + authToken);
                    }
                })
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new Implementation("admin-platform", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
        client.initialize();
        return client;
    }

    private void closeQuietly(McpSyncClient client) {
        try {
            client.close();
        } catch (Exception exception) {
            log.warn("关闭 MCP 客户端连接失败", exception);
        }
    }

    @PreDestroy
    public void closeAll() {
        clients.forEach((id, client) -> closeQuietly(client));
        clients.clear();
    }
}
