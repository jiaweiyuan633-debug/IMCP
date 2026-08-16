package com.example.admin.module.notice;

import com.example.admin.common.BusinessException;
import com.example.admin.common.SecretCipher;
import com.example.admin.module.notice.channel.ChannelFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.admin.module.notice.channel.MessageChannelSender;
import com.example.admin.module.notice.dto.ChannelSendRequest;
import com.example.admin.module.notice.entity.SysChannelConfigDO;
import com.example.admin.module.notice.entity.SysChannelLogDO;
import com.example.admin.module.notice.mapper.SysChannelConfigMapper;
import com.example.admin.module.notice.mapper.SysChannelLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 渠道发送重试核心逻辑测试：
 * sender.send 以返回非 null 表示失败，{@link ChannelConfigService#sendWithRetry} 将其包装为
 * {@link ChannelSendException} 以触发 spring-retry；业务参数错误不重试。
 * （单元测试不经过 Spring 代理，验证方法语义；重试次数由 @Retryable 在集成环境生效。）
 */
class ChannelConfigServiceRetryTest {

    private SysChannelConfigMapper configMapper;
    private SysChannelLogMapper logMapper;
    private ChannelFactory factory;
    private MessageChannelSender sender;
    private ChannelConfigService service;

    @BeforeEach
    void setUp() {
        configMapper = mock(SysChannelConfigMapper.class);
        logMapper = mock(SysChannelLogMapper.class);
        factory = mock(ChannelFactory.class);
        sender = mock(MessageChannelSender.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SecretCipher secretCipher = new SecretCipher("unit-test-encryption-key-not-for-prod", null);
        ChannelConfigCipher cipher = new ChannelConfigCipher(secretCipher, objectMapper);
        service = new ChannelConfigService(configMapper, logMapper, factory, objectMapper, cipher, secretCipher);
    }

    private SysChannelConfigDO enabledConfig() {
        SysChannelConfigDO config = new SysChannelConfigDO();
        config.setId(1L);
        config.setChannelType("MAIL");
        config.setChannelName("测试邮箱");
        config.setStatus(1);
        return config;
    }

    private ChannelSendRequest request() {
        ChannelSendRequest request = new ChannelSendRequest();
        request.setChannelId(1L);
        request.setTarget("a@example.com");
        request.setTitle("标题");
        request.setContent("内容");
        return request;
    }

    @Test
    void sendWithRetryReturnsLogIdOnSuccess() {
        when(configMapper.selectById(1L)).thenReturn(enabledConfig());
        when(factory.get(ChannelType.MAIL)).thenReturn(sender);
        when(sender.send(any(), any(), any(), any())).thenReturn(null);
        when(logMapper.insert(any(SysChannelLogDO.class))).thenAnswer(invocation -> {
            ((SysChannelLogDO) invocation.getArgument(0)).setId(10L);
            return 1;
        });
        SysChannelLogDO successLog = new SysChannelLogDO();
        successLog.setId(10L);
        successLog.setStatus(1);
        when(logMapper.selectById(10L)).thenReturn(successLog);

        Long logId = service.sendWithRetry(request());

        assertThat(logId).isEqualTo(10L);
    }

    @Test
    void sendWithRetryThrowsChannelSendExceptionOnFailure() {
        when(configMapper.selectById(1L)).thenReturn(enabledConfig());
        when(factory.get(ChannelType.MAIL)).thenReturn(sender);
        when(sender.send(any(), any(), any(), any())).thenReturn("SMTP timeout");
        when(logMapper.insert(any(SysChannelLogDO.class))).thenAnswer(invocation -> {
            ((SysChannelLogDO) invocation.getArgument(0)).setId(11L);
            return 1;
        });
        SysChannelLogDO failureLog = new SysChannelLogDO();
        failureLog.setId(11L);
        failureLog.setStatus(0);
        failureLog.setErrorMsg("SMTP timeout");
        when(logMapper.selectById(11L)).thenReturn(failureLog);

        assertThatThrownBy(() -> service.sendWithRetry(request()))
                .isInstanceOf(ChannelSendException.class)
                .hasMessageContaining("SMTP timeout");
    }

    @Test
    void sendWithRetryPropagatesBusinessErrorWithoutRetryMatch() {
        when(configMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.sendWithRetry(request()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sendWithRetryRejectsDisabledChannel() {
        SysChannelConfigDO config = enabledConfig();
        config.setStatus(0);
        when(configMapper.selectById(1L)).thenReturn(config);

        assertThatThrownBy(() -> service.sendWithRetry(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("停用");
        when(factory.get(eq(ChannelType.MAIL))).thenThrow(new AssertionError("不应解析渠道"));
    }
}
