package com.example.admin.module.ai;

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
import com.example.admin.module.ai.manager.AiTaskManager;
import com.example.admin.module.ai.vo.AiTaskResultVo;
import com.example.admin.module.ai.vo.AiTaskVo;
import com.example.admin.module.system.DataScopeHelper;
import com.example.admin.common.TenantContext;
import com.example.admin.common.annotation.DataScope;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String TASK_NO_PREFIX = "AI";
    private static final int TASK_NO_RANDOM_DIGITS = 4;
    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final Set<AiTaskStatus> TERMINAL_STATUS = EnumSet.of(
            AiTaskStatus.SUCCEEDED,
            AiTaskStatus.FAILED,
            AiTaskStatus.CANCELLED);

    private final AiTaskMapper taskMapper;
    private final AiTaskResultMapper resultMapper;
    private final AiServiceConfigMapper configMapper;
    private final AiTaskManager aiTaskManager;
    private final ObjectMapper objectMapper;
    private final DataScopeHelper dataScopeHelper;
    private final StringRedisTemplate redisTemplate;

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
        if (config.getDailyLimit() != null) {
            String key = "ai:limit:" + TenantContext.getTenantId() + ":" + config.getCode() + ":" + LocalDate.now();
            Long todayCount = redisTemplate.opsForValue().increment(key);
            if (todayCount != null && todayCount == 1) {
                redisTemplate.expire(key, Duration.ofDays(1));
            }
            if (todayCount != null && todayCount > config.getDailyLimit()) {
                redisTemplate.opsForValue().decrement(key);
                throw new BusinessException(ResultCode.AI_DAILY_LIMIT_EXCEEDED);
            }
        }

        String taskNo = TASK_NO_PREFIX + LocalDateTime.now().format(TASK_NO_FORMATTER)
                + RandomUtil.randomNumbers(TASK_NO_RANDOM_DIGITS);
        AiTask task = new AiTask();
        task.setTenantId(TenantContext.getTenantId());
        task.setTaskNo(taskNo);
        task.setBizType(request.getBizType());
        task.setBizId(request.getBizId());
        task.setServiceCode(config.getCode());
        task.setStatus(AiTaskStatus.PENDING.name());
        task.setParamsJson(toJson(request.getParams()));
        task.setRetryCount(0);
        task.setMaxRetry(DEFAULT_MAX_RETRY);
        task.setTimeoutSeconds(config.getTimeoutSeconds() == null
                ? DEFAULT_TIMEOUT_SECONDS
                : config.getTimeoutSeconds());
        task.setCallbackUrl(callbackBaseUrl + "/api/ai/callback/task");
        task.setCreatedBy(tryGetUserId());
        taskMapper.insert(task);

        try {
            if (StringUtils.hasText(aiBaseUrl)) {
                config.setBaseUrl(aiBaseUrl);
            }
            aiTaskManager.submit(
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

    @DataScope(tables = {"ai_task"})
    public PageResult<AiTaskVo> page(AiTaskQuery query) {
        Page<AiTask> page = new Page<>(query.getPageNum(), query.getPageSize(), false);
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(StringUtils.hasText(query.getStatus()), AiTask::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getBizType()), AiTask::getBizType, query.getBizType())
                .orderByDesc(AiTask::getId);
        IPage<AiTask> result = taskMapper.selectPage(page, wrapper);
        page.setTotal(taskMapper.selectCount(wrapper));
        List<AiTaskVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public AiTaskVo detail(Long id) {
        AiTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        checkDataScope(task);
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
        checkDataScope(task);
        if (isTerminal(task.getStatus())) {
            return;
        }
        task.setStatus(AiTaskStatus.CANCELLED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void handleCallback(AiCallbackRequest request, String token) {
        AiTask task = taskMapper.selectByTaskNoIgnoreTenant(request.getTaskNo());
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "AI 任务不存在");
        }
        TenantContext.setTenantId(task.getTenantId());
        AiServiceConfig config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfig>()
                .eq(AiServiceConfig::getCode, task.getServiceCode()));
        if (config == null || !StringUtils.hasText(config.getApiKey())
                || !config.getApiKey().equals(token)) {
            throw new BusinessException(ResultCode.AI_CALLBACK_INVALID);
        }
        if (isTerminal(task.getStatus())) {
            return;
        }
        String status = request.getStatus();
        if (!AiTaskStatus.SUCCEEDED.name().equals(status)
                && !AiTaskStatus.FAILED.name().equals(status)) {
            throw new BusinessException(ResultCode.AI_CALLBACK_STATUS_INVALID);
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
        } catch (BusinessException exception) {
            return null;
        }
    }

    private void checkDataScope(AiTask task) {
        if (dataScopeHelper.isAdmin()) {
            return;
        }
        List<Long> allowedUserIds = dataScopeHelper.allowedUserIds();
        if (allowedUserIds == null) {
            return;
        }
        if (task.getCreatedBy() == null || !allowedUserIds.contains(task.getCreatedBy())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private boolean isTerminal(String status) {
        try {
            return TERMINAL_STATUS.contains(AiTaskStatus.valueOf(status));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
