package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.SsrfUrlValidator;
import com.example.admin.common.SecretCipher;
import com.example.admin.module.ai.dto.AiConfigSaveRequest;
import com.example.admin.module.ai.entity.AiServiceConfigDO;
import com.example.admin.module.ai.mapper.AiServiceConfigMapper;
import com.example.admin.module.ai.vo.AiConfigVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiConfigService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_DAILY_LIMIT = 1000;
    private static final int ENABLED = 1;

    private final AiServiceConfigMapper configMapper;
    private final SecretCipher secretCipher;

    public List<AiConfigVo> list() {
        return configMapper.selectList(new LambdaQueryWrapper<AiServiceConfigDO>()
                        .orderByAsc(AiServiceConfigDO::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public void update(AiConfigSaveRequest request) {
        AiServiceConfigDO config = configMapper.selectById(request.getId());
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        config.setName(request.getName());
        config.setProvider(request.getProvider());
        config.setModel(request.getModel());
        // R4-1.44：AI 服务地址与告警 Webhook/通用渠道/MCP Server 对齐，保存时静态 SSRF 校验
        // （协议/主机/IP 字面量）——此前 baseUrl 无任何校验，管理员可配内网/云元数据地址，
        // 任务提交时服务端被打成内网探测跳板；投递前还有 DNS 复核（AiPythonClient）兜底
        String ssrfError = SsrfUrlValidator.validateOutboundHttpUrl(request.getBaseUrl());
        if (ssrfError != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "AI 服务地址不合法：" + ssrfError);
        }
        config.setBaseUrl(request.getBaseUrl());
        // R4-1.40：apiKey 落库加密（SecretCipher，"enc:" 前缀幂等跳过）——此前明文落库，
        // 与批10 渠道敏感字段加密标准不一致，数据库泄露即模型凭据泄露。回显仅 hasApiKey
        // 布尔，编辑留空不改；加密后下游发送/回调 HMAC 路径在使用前统一解密。
        if (StringUtils.hasText(request.getApiKey()) && !secretCipher.isEncrypted(request.getApiKey())) {
            config.setApiKey(secretCipher.encrypt(request.getApiKey()));
        }
        config.setTimeoutSeconds(request.getTimeoutSeconds() == null
                ? DEFAULT_TIMEOUT_SECONDS
                : request.getTimeoutSeconds());
        config.setEnabled(request.getEnabled() == null ? ENABLED : request.getEnabled());
        config.setDailyLimit(request.getDailyLimit() == null ? DEFAULT_DAILY_LIMIT : request.getDailyLimit());
        configMapper.updateById(config);
    }

    private AiConfigVo toVo(AiServiceConfigDO config) {
        return AiConfigVo.builder()
                .id(config.getId())
                .code(config.getCode())
                .provider(config.getProvider())
                .name(config.getName())
                .model(config.getModel())
                .baseUrl(config.getBaseUrl())
                .hasApiKey(StringUtils.hasText(config.getApiKey()))
                .timeoutSeconds(config.getTimeoutSeconds())
                .enabled(config.getEnabled())
                .dailyLimit(config.getDailyLimit())
                .build();
    }
}

