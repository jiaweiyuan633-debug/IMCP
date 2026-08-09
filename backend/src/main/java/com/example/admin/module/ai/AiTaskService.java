package com.example.admin.module.ai;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.ai.dto.AiCallbackRequest;
import com.example.admin.module.ai.dto.AiTaskCreateRequest;
import com.example.admin.module.ai.dto.AiTaskQuery;
import com.example.admin.module.ai.entity.AiServiceConfig;
import com.example.admin.module.ai.entity.AiTask;
import com.example.admin.module.ai.entity.AiTaskResult;
import com.example.admin.module.ai.mapper.AiServiceConfigMapper;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.module.ai.mapper.AiTaskResultMapper;
import com.example.admin.module.ai.vo.AiTaskResultVo;
import com.example.admin.module.ai.vo.AiTaskVo;
import com.example.admin.module.system.DataScopeHelper;
import com.example.admin.common.TenantContext;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private static final Set<String> TERMINAL_STATUS = Set.of(
            AiTaskStatus.SUCCEEDED.name(),
            AiTaskStatus.FAILED.name(),
            AiTaskStatus.CANCELLED.name());

    private final AiTaskMapper taskMapper;
    private final AiTaskResultMapper resultMapper;
    private final AiServiceConfigMapper configMapper;
    private final AiPythonClient pythonClient;
    private final ObjectMapper objectMapper;
    private final DataScopeHelper dataScopeHelper;

    @Value("${app.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Value("${app.ai-base-url:}")
    private String aiBaseUrl;

    @Transactional
    public Long create(AiTaskCreateRequest request) {
        AiServiceConfig config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfig>()
                .eq(AiServiceConfig::getCode, request.getServiceCode()));
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException(ResultCode.AI_CONFIG_UNAVAILABLE);
        }

        String taskNo = "AI" + DateUtil.format(new Date(), "yyyyMMddHHmmssSSS") + RandomUtil.randomNumbers(4);
        AiTask task = new AiTask();
        task.setTenantId(TenantContext.getTenantId());
        task.setTaskNo(taskNo);
        task.setBizType(request.getBizType());
        task.setBizId(request.getBizId());
        task.setServiceCode(config.getCode());
        task.setStatus(AiTaskStatus.PENDING.name());
        task.setParamsJson(toJson(request.getParams()));
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setTimeoutSeconds(config.getTimeoutSeconds() == null ? 60 : config.getTimeoutSeconds());
        task.setCallbackUrl(callbackBaseUrl + "/api/ai/callback/task");
        task.setCreatedBy(tryGetUserId());
        taskMapper.insert(task);

        try {
            if (StringUtils.hasText(aiBaseUrl)) {
                config.setBaseUrl(aiBaseUrl);
            }
            pythonClient.createTask(
                    config,
                    taskNo,
                    request.getBizType(),
                    request.getParams() == null ? Map.of() : request.getParams(),
                    task.getCallbackUrl());
            task.setStatus(AiTaskStatus.QUEUED.name());
            taskMapper.updateById(task);
        } catch (BusinessException exception) {
            task.setStatus(AiTaskStatus.FAILED.name());
            task.setErrorMsg(exception.getMessage());
            taskMapper.updateById(task);
            throw exception;
        }
        return task.getId();
    }

    public PageResult<AiTaskVo> page(AiTaskQuery query) {
        Page<AiTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(StringUtils.hasText(query.getStatus()), AiTask::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getBizType()), AiTask::getBizType, query.getBizType())
                .orderByDesc(AiTask::getId);
        if (!dataScopeHelper.isAdmin()) {
            List<Long> userIds = dataScopeHelper.allowedUserIds();
            if (userIds != null) {
                if (userIds.size() == 1) {
                    wrapper.eq(AiTask::getCreatedBy, userIds.get(0));
                } else {
                    wrapper.in(AiTask::getCreatedBy, userIds);
                }
            }
        }
        IPage<AiTask> result = taskMapper.selectPage(page, wrapper);
        List<AiTaskVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public AiTaskVo detail(Long id) {
        AiTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        AiTaskVo vo = toVo(task);
        AiTaskResult aiTaskResult = resultMapper.selectOne(new LambdaQueryWrapper<AiTaskResult>()
                .eq(AiTaskResult::getTaskId, id)
                .orderByDesc(AiTaskResult::getId)
                .last("LIMIT 1"));
        if (aiTaskResult != null) {
            vo.setResult(toResultVo(aiTaskResult));
        }
        return vo;
    }

    public void cancel(Long id) {
        AiTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (TERMINAL_STATUS.contains(task.getStatus())) {
            return;
        }
        task.setStatus(AiTaskStatus.CANCELLED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void handleCallback(AiCallbackRequest request, String token) {
        AiTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskNo, request.getTaskNo()));
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "AI 任务不存在");
        }
        AiServiceConfig config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfig>()
                .eq(AiServiceConfig::getCode, task.getServiceCode()));
        if (config == null || !StringUtils.hasText(config.getApiKey())
                || !config.getApiKey().equals(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "AI 回调签名无效");
        }
        if (TERMINAL_STATUS.contains(task.getStatus())) {
            return;
        }
        String status = request.getStatus();
        if (!AiTaskStatus.SUCCEEDED.name().equals(status)
                && !AiTaskStatus.FAILED.name().equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "非法回调状态");
        }

        task.setStatus(status);
        task.setErrorMsg(request.getError());
        if (request.getRetryCount() != null) {
            task.setRetryCount(request.getRetryCount());
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        if (AiTaskStatus.SUCCEEDED.name().equals(status)) {
            AiTaskResult result = new AiTaskResult();
            result.setTenantId(TenantContext.getTenantId());
            result.setTaskId(task.getId());
            result.setResultType(task.getBizType());
            result.setResultJson(toJson(request.getResult()));
            result.setRawData(toJson(request));
            resultMapper.insert(result);
        }
    }

    private AiTaskVo toVo(AiTask task) {
        return AiTaskVo.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .serviceCode(task.getServiceCode())
                .status(task.getStatus())
                .paramsJson(task.getParamsJson())
                .errorMsg(task.getErrorMsg())
                .retryCount(task.getRetryCount())
                .maxRetry(task.getMaxRetry())
                .timeoutSeconds(task.getTimeoutSeconds())
                .callbackUrl(task.getCallbackUrl())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private AiTaskResultVo toResultVo(AiTaskResult result) {
        return AiTaskResultVo.builder()
                .id(result.getId())
                .taskId(result.getTaskId())
                .resultType(result.getResultType())
                .resultJson(result.getResultJson())
                .rawData(result.getRawData())
                .durationMs(result.getDurationMs())
                .createdAt(result.getCreatedAt())
                .build();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize AI data", exception);
            return String.valueOf(value);
        }
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception exception) {
            return null;
        }
    }
}
