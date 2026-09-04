package cn.admin.scaffold.module.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;

import java.util.List;

/**
 * 平台 MCP 工具提供方 SPI：平台向外部 MCP 客户端暴露的能力按提供方拆分、可插拔注册。
 *
 * <p>扩展方式：新增一组工具时实现本接口并注册为 Spring Bean，
 * {@link McpPlatformTools} 自动收集所有实现并聚合暴露，无需改动平台工具装配代码。
 */
public interface PlatformToolProvider {

    /** 该提供方暴露的工具集合（name/title/description + 调用处理）。 */
    List<SyncToolSpecification> toolSpecifications();
}
