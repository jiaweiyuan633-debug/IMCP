package com.example.admin.module.ai;

import com.example.admin.common.TenantContext;
import com.example.admin.module.ai.dto.AiChatRequest;
import com.example.admin.module.ai.entity.AiServiceConfigDO;
import com.example.admin.module.ai.mapper.AiServiceConfigMapper;
import com.example.admin.module.ai.vo.AiChatVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelGatewayTest {

    @Mock
    private AiServiceConfigMapper configMapper;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private AiChatRequest request(String content) {
        AiChatRequest request = new AiChatRequest();
        request.setServiceCode("chat");
        AiChatRequest.ChatMessage message = new AiChatRequest.ChatMessage();
        message.setRole("user");
        message.setContent(content);
        request.setMessages(List.of(message));
        return request;
    }

    @Test
    void routesByProviderName() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setProvider("openai");
        when(configMapper.selectOne(any())).thenReturn(config);

        LlmProvider openAiProvider = mock(LlmProvider.class);
        when(openAiProvider.providerNames()).thenReturn(Set.of("openai"));
        when(openAiProvider.chat(any(), any(), any(), any())).thenReturn("from-openai");

        // 命中 openai 后不再遍历，otherProvider 不参与路由，仅用于断言未被调用
        LlmProvider otherProvider = mock(LlmProvider.class);

        ModelGateway gateway = new ModelGateway(configMapper, List.of(openAiProvider, otherProvider),
                promptTemplateService, knowledgeService, redisTemplate);

        AiChatRequest request = request("你好");
        AiChatVo result = gateway.chat(request);

        assertEquals("from-openai", result.getContent());
        verify(openAiProvider).chat(any(), any(), any(), any());
        verify(otherProvider, never()).chat(any(), any(), any(), any());
    }

    @Test
    void unknownProviderFallsBackToFirst() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setProvider("unknown-provider");
        when(configMapper.selectOne(any())).thenReturn(config);

        LlmProvider fallback = mock(LlmProvider.class);
        when(fallback.providerNames()).thenReturn(Set.of("openai"));
        when(fallback.chat(any(), any(), any(), any())).thenReturn("fallback");

        LlmProvider named = mock(LlmProvider.class);
        when(named.providerNames()).thenReturn(Set.of("gemini"));

        ModelGateway gateway = new ModelGateway(configMapper, List.of(fallback, named),
                promptTemplateService, knowledgeService, redisTemplate);

        AiChatVo result = gateway.chat(request("hi"));

        assertEquals("fallback", result.getContent());
        verify(fallback).chat(any(), any(), any(), any());
        verify(named, never()).chat(any(), any(), any(), any());
    }

    @Test
    void emptyProviderFallsBackToFirst() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setProvider(null);
        when(configMapper.selectOne(any())).thenReturn(config);

        LlmProvider fallback = mock(LlmProvider.class);
        when(fallback.chat(any(), any(), any(), any())).thenReturn("default");

        ModelGateway gateway = new ModelGateway(configMapper, List.of(fallback),
                promptTemplateService, knowledgeService, redisTemplate);

        AiChatVo result = gateway.chat(request("hi"));

        assertEquals("default", result.getContent());
        verify(fallback).chat(any(), any(), any(), any());
    }
}
