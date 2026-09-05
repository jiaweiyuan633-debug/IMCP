package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.module.ai.entity.AiServiceConfigDO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大模型提供方 SPI：按 provider 分发到不同实现（OpenAI 兼容、本地 Ollama、Claude、Gemini 等）。
 *
 * <p>扩展方式：新增 provider 时实现本接口并注册为 Spring Bean，
 * {@link ModelGateway} 按 {@link AiServiceConfigDO#getProvider()} 自动路由；
 * 未命中任何实现的 provider 回退到默认实现（当前为 OpenAI 兼容）。
 */
public interface LlmProvider {

    /**
     * 该实现支持的 provider 标识集合（与 ai_service_config.provider 字段对应）。
     * 一个实现可支持多个标识（如 openai / local 均走 OpenAI 兼容协议）。
     */
    Set<String> providerNames();

    /**
     * 发起一次非流式对话补全，返回模型回复文本。
     *
     * @param config      启用中的 AI 服务配置（含 baseUrl / apiKey / provider）
     * @param model       请求指定模型；为空时由实现回退到 config.model
     * @param messages    已组装好的 system + 历史消息
     * @param temperature 采样温度；为空时由实现决定默认
     */
    String chat(AiServiceConfigDO config, String model, List<Map<String, String>> messages, Double temperature);
}
