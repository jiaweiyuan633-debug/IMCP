package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.RequestIdHolder;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.SsrfUrlValidator;
import cn.admin.scaffold.module.ai.entity.AiServiceConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiPythonClient {

    private final RestTemplate restTemplate;

    public Map<String, Object> createTask(
            AiServiceConfigDO config,
            String taskNo,
            String bizType,
            Map<String, Object> params,
            String callbackUrl) {
        Map<String, Object> body = new HashMap<>(8);
        body.put("task_no", taskNo);
        body.put("biz_type", bizType);
        body.put("params", params == null ? Map.of() : params);
        body.put("callback_url", callbackUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 任务接口鉴权：AI 服务校验 Authorization: Bearer（与回调 HMAC 共用 ApiServiceConfig.apiKey）
        if (StringUtils.hasText(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey());
        }
        if (RequestIdHolder.get() != null) {
            headers.set("X-Request-Id", RequestIdHolder.get());
        }
        // R4-1.44：投递前 DNS 复核（对齐告警 Webhook/通用渠道/MCP）——保存时静态校验
        // 兜不住"主机名指向内网 IP"与"保存后 DNS 变更"，解析复核任一地址落内部网段即拒
        assertSafeOutboundUrl(config);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    config.getBaseUrl() + "/api/v1/tasks",
                    new HttpEntity<>(body, headers),
                    Map.class);
            return response.getBody() == null ? Map.of() : response.getBody();
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * R4-1.25：手动重试终态失败任务，调用 AI 侧 POST /api/v1/tasks/{taskNo}/retry。
     * AI 侧语义：任务置回 QUEUED、清空 error/reason 重新入队（保留已耗重试次数——
     * 重试给任务再一次执行机会而非重置完整重试预算）。404（AI 侧任务已过期/缺失）
     * 等调用错误统一映射为业务异常，由调用方按单条失败处理，不中断整批。
     */
    public void retryTask(AiServiceConfigDO config, String taskNo) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(config.getApiKey())) {
            headers.setBearerAuth(config.getApiKey());
        }
        if (RequestIdHolder.get() != null) {
            headers.set("X-Request-Id", RequestIdHolder.get());
        }
        // R4-1.44：投递前 DNS 复核，同 createTask
        assertSafeOutboundUrl(config);
        try {
            restTemplate.postForEntity(
                    config.getBaseUrl() + "/api/v1/tasks/" + taskNo + "/retry",
                    new HttpEntity<>(headers),
                    Map.class);
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    /** 出站 AI 服务地址投递前 DNS 复核（R4-1.44，对齐告警 Webhook/通用渠道/MCP Server）。 */
    private void assertSafeOutboundUrl(AiServiceConfigDO config) {
        String ssrfError = SsrfUrlValidator.validateOutboundHttpUrlWithDns(config.getBaseUrl());
        if (ssrfError != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "AI 服务地址校验失败：" + ssrfError);
        }
    }
}

