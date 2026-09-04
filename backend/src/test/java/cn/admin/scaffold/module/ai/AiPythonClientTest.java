package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.module.ai.entity.AiServiceConfigDO;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AI 出站调用投递前 SSRF 复核单测（R4-1.44 批次17）。
 *
 * <p>baseUrl 内部地址（回环 IP）在静态校验阶段即被拦截，RestTemplate 不应被调用；
 * 恶意/误配的 AI 服务地址不能在任务提交时把服务端当内网探测跳板。
 */
class AiPythonClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final AiPythonClient client = new AiPythonClient(restTemplate);

    @Test
    void createTaskRejectsInternalBaseUrlBeforeRequest() {
        AiServiceConfigDO config = configWithBaseUrl("http://127.0.0.1:8000");

        assertThatThrownBy(() -> client.createTask(config, "T1", "text_summary", Map.of(), "http://cb.example"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务地址校验失败");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void retryTaskRejectsInternalBaseUrlBeforeRequest() {
        AiServiceConfigDO config = configWithBaseUrl("http://localhost:8000");

        assertThatThrownBy(() -> client.retryTask(config, "T1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务地址校验失败");
        verifyNoInteractions(restTemplate);
    }

    private AiServiceConfigDO configWithBaseUrl(String baseUrl) {
        AiServiceConfigDO config = new AiServiceConfigDO();
        config.setBaseUrl(baseUrl);
        config.setApiKey("sk-test");
        return config;
    }
}
