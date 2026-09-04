package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SecretCipher;
import cn.admin.scaffold.module.ai.dto.AiConfigSaveRequest;
import cn.admin.scaffold.module.ai.entity.AiServiceConfigDO;
import cn.admin.scaffold.module.ai.mapper.AiServiceConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 服务配置凭据加密单测（R4-1.40 批次13）。
 *
 * <p>apiKey 落库统一改 SecretCipher 密文（此前明文落库）：明文提交时加密入库，
 * 空串/已 "enc:" 前缀提交幂等跳过（编辑留空不改、前端回显仅 hasApiKey 布尔）。
 */
@ExtendWith(MockitoExtension.class)
class AiConfigServiceTest {

    @Mock
    private AiServiceConfigMapper configMapper;

    @Mock
    private SecretCipher secretCipher;

    @InjectMocks
    private AiConfigService aiConfigService;

    @Test
    void updateEncryptsPlaintextApiKey() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setId(1L);
        when(configMapper.selectById(1L)).thenReturn(config);
        when(secretCipher.isEncrypted("sk-plain")).thenReturn(false);
        when(secretCipher.encrypt("sk-plain")).thenReturn("enc:xyz");

        aiConfigService.update(baseRequest(1L, "sk-plain"));

        ArgumentCaptor<AiServiceConfigDO> captor = ArgumentCaptor.forClass(AiServiceConfigDO.class);
        verify(configMapper).updateById(captor.capture());
        assertEquals("enc:xyz", captor.getValue().getApiKey());
    }

    @Test
    void updateKeepsExistingKeyWhenApiKeyBlank() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setId(1L);
        config.setApiKey("enc:existing");
        when(configMapper.selectById(1L)).thenReturn(config);

        aiConfigService.update(baseRequest(1L, ""));

        ArgumentCaptor<AiServiceConfigDO> captor = ArgumentCaptor.forClass(AiServiceConfigDO.class);
        verify(configMapper).updateById(captor.capture());
        assertEquals("enc:existing", captor.getValue().getApiKey());
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    void updateSkipsAlreadyEncryptedApiKey() {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setId(1L);
        config.setApiKey("enc:old");
        when(configMapper.selectById(1L)).thenReturn(config);
        when(secretCipher.isEncrypted("enc:new")).thenReturn(true);

        aiConfigService.update(baseRequest(1L, "enc:new"));

        ArgumentCaptor<AiServiceConfigDO> captor = ArgumentCaptor.forClass(AiServiceConfigDO.class);
        verify(configMapper).updateById(captor.capture());
        // 已带 "enc:" 前缀的提交值不二次加密，沿用库中原密文
        assertEquals("enc:old", captor.getValue().getApiKey());
        verify(secretCipher, never()).encrypt(any());
    }

    @Test
    void updateThrowsWhenConfigMissing() {
        when(configMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> aiConfigService.update(baseRequest(9L, "sk-plain")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.DATA_NOT_FOUND.getCode());
    }

    @Test
    void updateRejectsInternalBaseUrlOnSave() {
        // R4-1.44：AI 服务 baseUrl 保存时静态 SSRF 校验（对齐 webhook/MCP）——
        // 管理员配置内网/元数据地址会在任务提交时把服务端打成内网探测跳板
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setId(1L);
        when(configMapper.selectById(1L)).thenReturn(config);

        AiConfigSaveRequest request = baseRequest(1L, "sk-plain");
        request.setBaseUrl("http://127.0.0.1:8000");

        assertThatThrownBy(() -> aiConfigService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不合法");
        // R4-1.44：updateById(T) 与 updateById(Collection<T>) 双重重载下 any() 歧义，
        // 须显式限定参数类型
        verify(configMapper, never()).updateById(any(AiServiceConfigDO.class));
    }

    private AiConfigSaveRequest baseRequest(Long id, String apiKey) {
        AiConfigSaveRequest request = new AiConfigSaveRequest();
        request.setId(id);
        request.setName("文本摘要");
        request.setProvider("openai");
        request.setModel("gpt-4o");
        request.setBaseUrl("https://api.openai.com/v1");
        request.setApiKey(apiKey);
        request.setTimeoutSeconds(30);
        request.setEnabled(1);
        request.setDailyLimit(100);
        return request;
    }
}
