package com.example.admin.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.ai.dto.AiConfigSaveRequest;
import com.example.admin.module.ai.entity.AiServiceConfig;
import com.example.admin.module.ai.mapper.AiServiceConfigMapper;
import com.example.admin.module.ai.vo.AiConfigVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final AiServiceConfigMapper configMapper;

    public List<AiConfigVo> list() {
        return configMapper.selectList(new LambdaQueryWrapper<AiServiceConfig>()
                        .orderByAsc(AiServiceConfig::getId))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public void update(AiConfigSaveRequest request) {
        AiServiceConfig config = configMapper.selectById(request.getId());
        if (config == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        config.setName(request.getName());
        config.setBaseUrl(request.getBaseUrl());
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            config.setApiKey(request.getApiKey());
        }
        config.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 60 : request.getTimeoutSeconds());
        config.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        configMapper.updateById(config);
    }

    private AiConfigVo toVo(AiServiceConfig config) {
        return AiConfigVo.builder()
                .id(config.getId())
                .code(config.getCode())
                .name(config.getName())
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .timeoutSeconds(config.getTimeoutSeconds())
                .enabled(config.getEnabled())
                .build();
    }
}

