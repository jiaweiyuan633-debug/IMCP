package cn.admin.scaffold.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.ai.dto.AiChatRequest;
import cn.admin.scaffold.module.ai.entity.AiServiceConfigDO;
import cn.admin.scaffold.module.ai.mapper.AiServiceConfigMapper;
import cn.admin.scaffold.module.ai.vo.AiChatVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 模型网关：加载启用配置、共享日限额（与任务派发同一 Redis 键）、
 * 组装 system（Prompt 渲染 + RAG 上下文）与历史消息，直连 OpenAI 兼容接口。
 */
@Service
@RequiredArgsConstructor
public class ModelGateway {

    private final AiServiceConfigMapper configMapper;
    private final List<LlmProvider> llmProviders;
    private final PromptTemplateService promptTemplateService;
    private final KnowledgeService knowledgeService;
    private final StringRedisTemplate redisTemplate;
    private final SecretCipher secretCipher;

    public AiChatVo chat(AiChatRequest request) {
        AiServiceConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigDO>()
                .eq(AiServiceConfigDO::getCode, request.getServiceCode())
                .eq(AiServiceConfigDO::getEnabled, 1)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(ResultCode.AI_CONFIG_UNAVAILABLE);
        }
        Long tenantId = TenantContext.getTenantId();
        checkDailyLimit(tenantId, config);

        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getTemplateCode())) {
            messages.add(Map.of("role", "system",
                    "content", promptTemplateService.render(request.getTemplateCode(), request.getTemplateParams())));
        }
        String lastUserContent = lastUserContent(request.getMessages());
        if (Boolean.TRUE.equals(request.getUseKnowledge())
                && StringUtils.hasText(lastUserContent)) {
            String context = knowledgeService.buildContext(request.getKnowledgeBaseId(), lastUserContent, request.getTopK());
            if (StringUtils.hasText(context)) {
                messages.add(Map.of("role", "system", "content", context));
            }
        }
        if (request.getMessages() != null) {
            request.getMessages().stream()
                    .filter(m -> m.getRole() != null && m.getContent() != null)
                    .forEach(m -> messages.add(Map.of("role", m.getRole(), "content", m.getContent())));
        }

        // apiKey 落库为 SecretCipher 密文，直连 LLM 前解密为明文供 Bearer 鉴权
        config.setApiKey(secretCipher.decrypt(config.getApiKey()));
        long start = System.currentTimeMillis();
        String content = resolveProvider(config.getProvider())
                .chat(config, request.getModel(), messages, request.getTemperature());
        long durationMs = System.currentTimeMillis() - start;

        return AiChatVo.builder()
                .content(content)
                .model(StringUtils.hasText(request.getModel()) ? request.getModel() : config.getModel())
                .provider(config.getProvider())
                .durationMs(durationMs)
                .status(1)
                .build();
    }

    /** 共享任务派发同一日限额键（ai:limit:{tenant}:{code}:{yyyy-MM-dd}）。 */
    private void checkDailyLimit(Long tenantId, AiServiceConfigDO config) {
        if (config.getDailyLimit() == null) {
            return;
        }
        String key = "ai:limit:" + tenantId + ":" + config.getCode() + ":" + LocalDate.now();
        Long todayCount = redisTemplate.opsForValue().increment(key);
        if (todayCount != null && todayCount == 1) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        if (todayCount != null && todayCount > config.getDailyLimit()) {
            redisTemplate.opsForValue().decrement(key);
            throw new BusinessException(ResultCode.AI_DAILY_LIMIT_EXCEEDED);
        }
    }

    private String lastUserContent(List<AiChatRequest.ChatMessage> messages) {
        if (messages == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatRequest.ChatMessage message = messages.get(i);
            if (message.getRole() != null && "user".equalsIgnoreCase(message.getRole())) {
                return message.getContent();
            }
        }
        return null;
    }

    /** 按 provider 路由到对应 {@link LlmProvider} 实现；未命中或为空时回退到默认实现（OpenAI 兼容）。 */
    private LlmProvider resolveProvider(String provider) {
        if (StringUtils.hasText(provider)) {
            for (LlmProvider candidate : llmProviders) {
                if (candidate.providerNames().contains(provider)) {
                    return candidate;
                }
            }
        }
        return llmProviders.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("未注册任何 LlmProvider 实现"));
    }
}
