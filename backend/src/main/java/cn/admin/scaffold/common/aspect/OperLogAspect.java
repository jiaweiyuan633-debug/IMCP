package cn.admin.scaffold.common.aspect;

import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.entity.SysOperLogDO;
import cn.admin.scaffold.module.system.mapper.SysOperLogMapper;
import cn.admin.scaffold.module.system.entity.SysAuditLogDO;
import cn.admin.scaffold.module.system.mapper.SysAuditLogMapper;
import cn.admin.scaffold.security.SecurityUtils;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.LogMaskUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final int MAX_RESULT_LENGTH = 2000;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 0;

    private final SysOperLogMapper operLogMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics businessMetrics;
    private final TaskExecutor operLogExecutor;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            error = throwable;
            throw throwable;
        } finally {
            asyncSaveLog(joinPoint, operLog, start, result, error);
        }
    }

    /**
     * 日志异步落库，主请求路径不再承担 2 次日志 INSERT。
     * 上下文（租户/用户/请求信息）依赖 ThreadLocal，必须在调用线程提取完毕后
     * 才提交异步任务；异步线程只做纯落库与指标计数。
     * 队列拥堵时回退调用线程同步写（CallerRunsPolicy），日志不丢失。
     */
    /** 包内可见：单元测试直接驱动异步提交流程。 */
    void asyncSaveLog(ProceedingJoinPoint joinPoint, OperLog operLog, long start, Object result, Throwable error) {
        LogEntities entities;
        try {
            entities = buildEntities(joinPoint, operLog, start, result, error);
        } catch (RuntimeException exception) {
            log.warn("Failed to build oper log", exception);
            return;
        }
        try {
            operLogExecutor.execute(() -> writeQuietly(entities));
        } catch (RejectedExecutionException exception) {
            log.warn("Oper log executor busy, fallback to sync write", exception);
            writeQuietly(entities);
        }
    }

    private void writeQuietly(LogEntities entities) {
        try {
            operLogMapper.insert(entities.operLog());
            auditLogMapper.insert(entities.auditLog());
            businessMetrics.operLogWritten();
        } catch (RuntimeException exception) {
            log.warn("Failed to write oper log", exception);
        }
    }

    /** 调用线程内构建日志实体（读取全部 ThreadLocal 上下文），包内可见便于单元测试。 */
    LogEntities buildEntities(ProceedingJoinPoint joinPoint, OperLog operLog, long start, Object result, Throwable error) {
        SysOperLogDO operLogEntity = new SysOperLogDO();
        operLogEntity.setTenantId(TenantContext.getTenantId());
        operLogEntity.setUserId(SecurityUtils.tryGetUserId());
        operLogEntity.setModule(operLog.module());
        operLogEntity.setAction(operLog.action());
        operLogEntity.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName());
        operLogEntity.setDurationMs(System.currentTimeMillis() - start);
        operLogEntity.setStatus(error == null ? STATUS_SUCCESS : STATUS_FAILURE);
        operLogEntity.setOperTime(LocalDateTime.now());
        if (error != null) {
            operLogEntity.setErrorMsg(truncate(error.getMessage(), MAX_ERROR_LENGTH));
        }

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operLogEntity.setRequestUrl(request.getRequestURI());
            operLogEntity.setRequestMethod(request.getMethod());
            operLogEntity.setIp(request.getRemoteAddr());
        }
        operLogEntity.setParams(toJson(filterArgs(joinPoint.getArgs()), operLog.maskFields()));
        operLogEntity.setResult(toJson(result, operLog.maskFields()));
        return new LogEntities(operLogEntity, buildAuditLog(operLogEntity));
    }

    private SysAuditLogDO buildAuditLog(SysOperLogDO operLogEntity) {
        SysAuditLogDO auditLog = new SysAuditLogDO();
        auditLog.setTenantId(operLogEntity.getTenantId());
        auditLog.setUserId(operLogEntity.getUserId());
        auditLog.setModule(operLogEntity.getModule());
        auditLog.setAction(operLogEntity.getAction());
        auditLog.setParams(operLogEntity.getParams());
        auditLog.setResult(operLogEntity.getResult());
        auditLog.setStatus(operLogEntity.getStatus());
        auditLog.setCreatedAt(LocalDateTime.now());
        return auditLog;
    }

    /** 操作日志 + 审计日志，作为一次异步任务原子提交。 */
    record LogEntities(SysOperLogDO operLog, SysAuditLogDO auditLog) {
    }

    private List<Object> filterArgs(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
                // MultipartFile 只保留元信息，不序列化二进制内容——Jackson valueToTree
                // 会对 MultipartFile 调用 getBytes() 整读文件进堆并 base64 序列化（大文件每次上传
                // 额外占一份完整文件内存），此前 CommonController/FileChunkController 的
                // @OperLog 已把文件字节写入入参 JSON
                .map(arg -> arg instanceof MultipartFile file ? fileMetadata(file) : arg)
                .toList();
    }

    /** 上传入参的文件元信息快照（不含内容）：记录"谁在何时上传了哪个文件"，避免二进制序列化。 */
    private Map<String, Object> fileMetadata(MultipartFile file) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originalFilename", file.getOriginalFilename());
        metadata.put("size", file.getSize());
        metadata.put("contentType", file.getContentType());
        return metadata;
    }

    /** 按注解 maskFields 对入参/结果中的发送类正文与参数整体打码（见 @OperLog.maskFields）。 */
    private String toJson(Object value, String[] maskFields) {
        if (value == null) {
            return null;
        }
        return truncate(LogMaskUtils.toMaskedJson(value, objectMapper, maskFields), MAX_RESULT_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

