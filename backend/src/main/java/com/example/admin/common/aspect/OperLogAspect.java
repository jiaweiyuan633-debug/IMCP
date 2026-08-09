package com.example.admin.common.aspect;

import com.example.admin.common.annotation.OperLog;
import com.example.admin.module.system.entity.SysOperLogDO;
import com.example.admin.module.system.mapper.SysOperLogMapper;
import com.example.admin.module.system.entity.SysAuditLogDO;
import com.example.admin.module.system.mapper.SysAuditLogMapper;
import com.example.admin.security.SecurityUtils;
import com.example.admin.common.TenantContext;
import com.example.admin.common.BusinessException;
import com.example.admin.common.LogMaskUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
            saveLog(joinPoint, operLog, start, result, error);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLog, long start, Object result, Throwable error) {
        try {
            SysOperLogDO operLogEntity = new SysOperLogDO();
            operLogEntity.setTenantId(TenantContext.getTenantId());
            operLogEntity.setUserId(tryGetUserId());
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
            operLogEntity.setParams(toJson(filterArgs(joinPoint.getArgs())));
            operLogEntity.setResult(toJson(result));
            operLogMapper.insert(operLogEntity);
            saveAuditLog(operLogEntity);
        } catch (RuntimeException exception) {
            log.warn("Failed to write oper log", exception);
        }
    }

    private void saveAuditLog(SysOperLogDO operLogEntity) {
        SysAuditLogDO auditLog = new SysAuditLogDO();
        auditLog.setTenantId(TenantContext.getTenantId());
        auditLog.setUserId(operLogEntity.getUserId());
        auditLog.setModule(operLogEntity.getModule());
        auditLog.setAction(operLogEntity.getAction());
        auditLog.setParams(operLogEntity.getParams());
        auditLog.setResult(operLogEntity.getResult());
        auditLog.setStatus(operLogEntity.getStatus());
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(auditLog);
    }

    private List<Object> filterArgs(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
                .toList();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        return truncate(LogMaskUtils.toMaskedJson(value, objectMapper), MAX_RESULT_LENGTH);
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (BusinessException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

