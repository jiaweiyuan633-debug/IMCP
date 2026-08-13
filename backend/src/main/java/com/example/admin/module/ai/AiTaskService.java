package com.example.admin.module.ai;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.MessageBizType;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.ai.dto.AiCallbackRequest;
import com.example.admin.module.ai.dto.AiTaskCreateRequest;
import com.example.admin.module.ai.dto.AiTaskQuery;
import com.example.admin.module.ai.entity.AiServiceConfigDO;
import com.example.admin.module.ai.entity.AiTaskDO;
import com.example.admin.module.ai.entity.AiTaskResultDO;
import com.example.admin.module.ai.mapper.AiServiceConfigMapper;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.module.ai.mapper.AiTaskResultMapper;
import com.example.admin.module.ai.manager.AiTaskManager;
import com.example.admin.module.ai.vo.AiTaskResultVo;
import com.example.admin.module.ai.vo.AiTaskRetryResult;
import com.example.admin.module.ai.vo.AiTaskVo;
import com.example.admin.module.system.DataScopeHelper;
import com.example.admin.module.system.SystemMessageService;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private static final DateTimeFormatter TASK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String TASK_NO_PREFIX = "AI";
    private static final int TASK_NO_RANDOM_DIGITS = 4;
    private static final int DEFAULT_MAX_RETRY = 3;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    // 回调时钟偏移容忍：与 ai-service app/core/config.py callback_clock_skew_seconds=300 保持一致
    private static final int CALLBACK_MAX_SKEW_SECONDS = 300;
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
    private final SystemMessageService messageService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    @Value("${app.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Value("${app.ai-base-url:}")
    private String aiBaseUrl;

    // 外部提交移出事务边界：任务先落库（PENDING，autocommit 立即可见），再同步提交 AI 服务。
    // 避免 DB 事务/连接被外部 HTTP 往返长时间占用；若 AI 侧已受理而本侧提交失败，
    // 任务仍保留在库中由 AiTaskScanner 按超时补偿，不再产生孤儿任务。
    public Long create(AiTaskCreateRequest request) {
        AiServiceConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigDO>()
                .eq(AiServiceConfigDO::getCode, request.getServiceCode()));
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
        AiTaskDO task = new AiTaskDO();
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
        task.setCreatedBy(SecurityUtils.tryGetUserId());
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
        Page<AiTaskDO> page = new Page<>(query.getPageNum(), query.getPageSize(), false);
        LambdaQueryWrapper<AiTaskDO> wrapper = new LambdaQueryWrapper<AiTaskDO>()
                .eq(StringUtils.hasText(query.getStatus()), AiTaskDO::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getBizType()), AiTaskDO::getBizType, query.getBizType())
                .eq(StringUtils.hasText(query.getErrorType()), AiTaskDO::getErrorType, query.getErrorType())
                .orderByDesc(AiTaskDO::getId);
        IPage<AiTaskDO> result = taskMapper.selectPage(page, wrapper);
        page.setTotal(taskMapper.selectCount(wrapper));
        List<AiTaskVo> records = result.getRecords().stream().map(this::toVo).toList();
        fillListDisplayNames(records);
        return PageResult.of(result, records);
    }

    /**
     * R4-1.24：列表展示名批量解析（避免按行 N+1 查询）。
     * <p>把已随 VO 暴露的 serviceCode / createdBy 解析为可读展示名：
     * 服务名来自 ai_service_config.name（租户内批量查询），创建人姓名来自 sys_user
     * （租户内 selectBatchIds，优先 nickname、回退 username）。均按 code/id 去重后
     * 单次查询；未命中（如服务被删、用户被逻辑删除）时服务名回退编码、姓名保持空，
     * 由前端兜底显示，不改变列表过滤语义。
     */
    private void fillListDisplayNames(List<AiTaskVo> records) {
        if (records.isEmpty()) {
            return;
        }
        Set<String> serviceCodes = records.stream()
                .map(AiTaskVo::getServiceCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (!serviceCodes.isEmpty()) {
            Map<String, String> nameByCode = configMapper.selectList(
                            new LambdaQueryWrapper<AiServiceConfigDO>()
                                    .select(AiServiceConfigDO::getCode, AiServiceConfigDO::getName)
                                    .in(AiServiceConfigDO::getCode, serviceCodes))
                    .stream()
                    .filter(config -> StringUtils.hasText(config.getName()))
                    .collect(Collectors.toMap(AiServiceConfigDO::getCode, AiServiceConfigDO::getName, (a, b) -> a));
            records.forEach(vo -> {
                if (StringUtils.hasText(vo.getServiceCode())) {
                    vo.setServiceName(nameByCode.getOrDefault(vo.getServiceCode(), vo.getServiceCode()));
                }
            });
        }
        Set<Long> createdBys = records.stream()
                .map(AiTaskVo::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!createdBys.isEmpty()) {
            Map<Long, String> nameById = userMapper.selectBatchIds(createdBys).stream()
                    .collect(Collectors.toMap(SysUserDO::getId,
                            user -> StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(),
                            (a, b) -> a));
            records.forEach(vo -> {
                if (vo.getCreatedBy() != null) {
                    vo.setCreatedByName(nameById.get(vo.getCreatedBy()));
                }
            });
        }
    }

    public AiTaskVo detail(Long id) {
        AiTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        checkDataScope(task);
        return toVoWithResult(task);
    }

    /**
     * R4-1.9：SSE 轮询专用读取（跳过 SecurityContext 数据范围校验）。
     * 连接建立时已在请求线程经 {@link #openStream} 完成访问校验并捕获租户；
     * 轮询线程无 SecurityContext/TenantContext，若复用 detail() 的 SecurityUtils 校验
     * 必然抛 UNAUTHORIZED 使流立即失败。调用方（AiTaskStreamService）须先就位
     * TenantContext（emit 内 setTenantId）再调用，否则 selectById 会被租户拦截器挡回。
     */
    public AiTaskVo detailForStream(Long taskId) {
        AiTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return toVoWithResult(task);
    }

    private AiTaskVo toVoWithResult(AiTaskDO task) {
        AiTaskVo vo = toVo(task);
        AiTaskResultDO aiTaskResult = resultMapper.selectOne(new LambdaQueryWrapper<AiTaskResultDO>()
                .eq(AiTaskResultDO::getTaskId, task.getId())
                .orderByDesc(AiTaskResultDO::getId)
                .last("LIMIT 1"));
        if (aiTaskResult != null) {
            vo.setResult(toResultVo(aiTaskResult));
        }
        return vo;
    }

    /** AI 任务 SSE 流连接上下文：轮询线程据此恢复租户并读取任务。 */
    public record TaskStreamContext(Long taskId, Long tenantId) {
    }

    /**
     * R4-1.9：SSE 流连接前的访问校验（请求线程执行）。
     * <p>流端点为 permitAll + 一次性票据鉴权，EventSource 无法携带 Authorization 头，
     * 请求线程既无 SecurityContext 也无租户头（TenantFilter 不再信任请求头，默认租户 1）。
     * 故先按票据 userId 跨租户定位用户（以库表租户为权威来源，与 JwtAuthenticationFilter
     * 一致），再按其租户上下文查任务并做归属校验：管理员可看全部，非管理员仅可看自己
     * 创建的任务（createdBy 匹配）。校验通过后返回带租户的上下文，供轮询线程恢复上下文。
     *
     * @throws BusinessException 用户不存在/停用（UNAUTHORIZED）、任务不存在（DATA_NOT_FOUND）、
     *                          无访问权（FORBIDDEN）
     */
    public TaskStreamContext openStream(Long taskId, Long userId) {
        SysUserDO user = userMapper.selectByIdIgnoreTenant(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        TenantContext.setTenantId(user.getTenantId());
        AiTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        boolean admin = roleMapper.selectRoleCodesByUserId(userId).contains("admin");
        if (!admin && (task.getCreatedBy() == null || !task.getCreatedBy().equals(userId))) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return new TaskStreamContext(taskId, task.getTenantId());
    }

    public void cancel(Long id) {
        AiTaskDO task = taskMapper.selectById(id);
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

    /**
     * R4-1.25：批量重试终态失败任务（死信恢复入口）。
     * <p>逐条处理：仅 FAILED 终态且服务仍可用（enabled=1）的任务会被重试——先调用 AI 侧
     * retry 重新入队，再条件更新把本库状态从 FAILED 置回 QUEUED 并清空 error/errorType。
     * 条件更新以 status=FAILED 为前置，避免与并发重试/已抢先到达的成功回调互踩：影响 0 行
     * 说明任务已被并发处理，计为跳过。数据范围与 detail/cancel 一致（checkDataScope）。
     * 单条 AI 调用失败（服务禁用/不可用/AI 侧任务已过期）仅影响该条，不中断整批。
     *
     * @return 批处理统计（成功/跳过/失败 + 失败 ID 列表）
     */
    public AiTaskRetryResult retry(List<Long> ids) {
        int succeeded = 0;
        int skipped = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Long id : ids) {
            AiTaskDO task = taskMapper.selectById(id);
            if (task == null) {
                skipped++;
                continue;
            }
            checkDataScope(task);
            if (!AiTaskStatus.FAILED.name().equals(task.getStatus())) {
                skipped++;
                continue;
            }
            AiServiceConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigDO>()
                    .eq(AiServiceConfigDO::getCode, task.getServiceCode()));
            if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
                log.warn("AI task {} retry skipped: config unavailable", task.getTaskNo());
                failedIds.add(id);
                continue;
            }
            try {
                aiTaskManager.retry(config, task.getTaskNo());
            } catch (BusinessException exception) {
                log.warn("AI task {} retry failed: {}", task.getTaskNo(), exception.getMessage());
                failedIds.add(id);
                continue;
            }
            int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiTaskDO>()
                    .eq(AiTaskDO::getId, id)
                    .eq(AiTaskDO::getStatus, AiTaskStatus.FAILED.name())
                    .set(AiTaskDO::getStatus, AiTaskStatus.QUEUED.name())
                    .set(AiTaskDO::getErrorMsg, null)
                    .set(AiTaskDO::getErrorType, null)
                    .set(AiTaskDO::getUpdatedAt, LocalDateTime.now()));
            if (updated == 1) {
                succeeded++;
            } else {
                skipped++;
            }
        }
        return AiTaskRetryResult.builder()
                .total(ids.size())
                .succeeded(succeeded)
                .skipped(skipped)
                .failed(failedIds.size())
                .failedIds(failedIds)
                .build();
    }

    @Transactional
    public void handleCallback(AiCallbackRequest request, byte[] rawBody, String timestamp, String signature) {
        AiTaskDO task = taskMapper.selectByTaskNoIgnoreTenant(request.getTaskNo());
        if (task == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "AI 任务不存在");
        }
        TenantContext.setTenantId(task.getTenantId());
        AiServiceConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigDO>()
                .eq(AiServiceConfigDO::getCode, task.getServiceCode()));
        if (config == null || !validCallbackHmac(config.getApiKey(), rawBody, timestamp, signature)) {
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

        // 条件 UPDATE 原子抢占终态：仅当任务仍处于非终态时才更新，重复/并发回调影响行数为 0 则直接忽略，
        // 避免重复插入结果行与重复推送通知（ai_task 无 version 列，check-then-act 存在竞态）
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiTaskDO>()
                .eq(AiTaskDO::getTaskNo, request.getTaskNo())
                .in(AiTaskDO::getStatus,
                        AiTaskStatus.PENDING.name(),
                        AiTaskStatus.QUEUED.name(),
                        AiTaskStatus.RUNNING.name())
                .set(AiTaskDO::getStatus, status)
                .set(AiTaskDO::getErrorMsg, request.getError())
                .set(AiTaskDO::getErrorType, request.getErrorType())
                .set(AiTaskDO::getRetryCount,
                        request.getRetryCount() == null ? task.getRetryCount() : request.getRetryCount())
                .set(AiTaskDO::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            return;
        }
        task.setStatus(status);
        task.setErrorMsg(request.getError());
        task.setErrorType(request.getErrorType());
        if (request.getRetryCount() != null) {
            task.setRetryCount(request.getRetryCount());
        }
        task.setUpdatedAt(LocalDateTime.now());

        if (AiTaskStatus.SUCCEEDED.name().equals(status)) {
            AiTaskResultDO result = new AiTaskResultDO();
            result.setTenantId(TenantContext.getTenantId());
            result.setTaskId(task.getId());
            result.setResultType(task.getBizType());
            result.setResultJson(toJson(request.getResult()));
            result.setRawData(toJson(request));
            resultMapper.insert(result);
        }
        notifyCreator(task, status);
    }

    private void notifyCreator(AiTaskDO task, String status) {
        if (task.getCreatedBy() == null) {
            return;
        }
        boolean succeeded = AiTaskStatus.SUCCEEDED.name().equals(status);
        String title = succeeded ? "AI 任务完成" : "AI 任务失败";
        String content = succeeded
                ? "AI 任务「" + task.getTaskNo() + "」已执行完成。"
                : "AI 任务「" + task.getTaskNo() + "」执行失败"
                        + (task.getErrorMsg() == null || task.getErrorMsg().isBlank() ? "。" : "：" + task.getErrorMsg());
        messageService.sendSystemToUsers(List.of(task.getCreatedBy()), task.getTenantId(),
                title, content, MessageBizType.AI, task.getId());
    }

    private AiTaskVo toVo(AiTaskDO task) {
        return AiTaskVo.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .serviceCode(task.getServiceCode())
                .status(task.getStatus())
                .paramsJson(task.getParamsJson())
                .errorMsg(task.getErrorMsg())
                .errorType(task.getErrorType())
                .retryCount(task.getRetryCount())
                .maxRetry(task.getMaxRetry())
                .timeoutSeconds(task.getTimeoutSeconds())
                .callbackUrl(task.getCallbackUrl())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private AiTaskResultVo toResultVo(AiTaskResultDO result) {
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

    private void checkDataScope(AiTaskDO task) {
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

    /**
     * 回调 HMAC-SHA256 校验，与 ai-service tasks/manager.py _callback 保持字节级一致：
     * message = timestamp + "\n" + rawBody，key = 服务配置 apiKey（= AI 侧 AUTH_TOKEN）。
     * 时间戳在 ±CALLBACK_MAX_SKEW_SECONDS 内有效；签名比较用常量时间避免时序侧信道。
     */
    private boolean validCallbackHmac(String apiKey, byte[] body, String timestamp, String signature) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > CALLBACK_MAX_SKEW_SECONDS) {
            return false;
        }
        byte[] head = (timestamp + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[head.length + body.length];
        System.arraycopy(head, 0, message, 0, head.length);
        System.arraycopy(body, 0, message, head.length, body.length);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexUtil.encodeHexStr(mac.doFinal(message))
                    .getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            log.warn("AI 回调 HMAC 计算失败", exception);
            return false;
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
