package com.example.admin.config;

import com.example.admin.module.mcp.McpPlatformTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * MCP Server 配置：通过 SSE 端点 /mcp 对外暴露平台只读工具，
 * 供 Claude Desktop 等外部 MCP 客户端连接调用。
 */
@Slf4j
@Configuration
public class McpServerConfig {

    public static final String MCP_SSE_ENDPOINT = "/mcp";
    public static final String MCP_MESSAGE_ENDPOINT = "/mcp/message";

    @Bean
    public WebMvcSseServerTransportProvider mcpTransportProvider(ObjectMapper objectMapper) {
        return WebMvcSseServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .sseEndpoint(MCP_SSE_ENDPOINT)
                .messageEndpoint(MCP_MESSAGE_ENDPOINT)
                .build();
    }

    /** 注册 SSE 传输路由到 Spring MVC。 */
    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransportProvider mcpTransportProvider) {
        return mcpTransportProvider.getRouterFunction();
    }

    /** 平台 MCP 服务：注册只读工具并启动。 */
    @Bean(destroyMethod = "close")
    public McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider mcpTransportProvider,
                                       McpPlatformTools platformTools) {
        log.info("启动平台 MCP Server，SSE 端点：{}", MCP_SSE_ENDPOINT);
        return McpServer.sync(mcpTransportProvider)
                .serverInfo("admin-platform", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(false)
                        .build())
                .tools(platformTools.toolSpecifications())
                .build();
    }
}
