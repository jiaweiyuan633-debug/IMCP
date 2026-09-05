package cn.admin.scaffold.common.aspect;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.config.MybatisPlusConfig;
import cn.admin.scaffold.common.FieldDiffUtils;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.annotation.FieldAudit;
import cn.admin.scaffold.module.system.entity.SysFieldAuditLogDO;
import cn.admin.scaffold.module.system.mapper.SysFieldAuditLogMapper;
import cn.admin.scaffold.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 字段级审计切面。
 *
 * <p>拦截 {@link FieldAudit} 标注的方法：执行前按参数主键读取旧快照，执行后
 * 从数据库重读新快照（或直接取参数实体），计算字段级 diff 写入审计表。
 * 审计链路全程 try-catch，失败仅记录日志，绝不阻断业务主流程。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class FieldAuditAspect {

    private static final int MAX_SNAPSHOT_LENGTH = 10000;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILURE = 0;

    private final SysFieldAuditLogMapper fieldAuditLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics businessMetrics;

    @Around("@annotation(fieldAudit)")
    public Object around(ProceedingJoinPoint joinPoint, FieldAudit fieldAudit) throws Throwable {
        Object before = null;
        try {
            Long id = extractId(fieldAudit.entity(), joinPoint.getArgs());
            if (id != null) {
                before = selectById(fieldAudit.entity(), id);
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to capture before-snapshot for field audit", exception);
        }
        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            error = throwable;
            throw throwable;
        } finally {
            saveAudit(joinPoint, fieldAudit, before, error);
        }
    }

    private void saveAudit(ProceedingJoinPoint joinPoint, FieldAudit fieldAudit, Object before, Throwable error) {
        try {
            Long id = extractId(fieldAudit.entity(), joinPoint.getArgs());
            Object after;
            if (id != null) {
                // 事务内重读：拿到更新后的数据库快照，保证全字段完整
                after = selectById(fieldAudit.entity(), id);
            } else {
                // 新增场景：主键尚未生成，取参数中的实体作为新快照
                after = firstEntityArg(fieldAudit.entity(), joinPoint.getArgs());
            }
            List<FieldDiffUtils.Change> changes = FieldDiffUtils.diff(before, after);

            SysFieldAuditLogDO record = new SysFieldAuditLogDO();
            record.setTenantId(TenantContext.getTenantId());
            record.setUserId(SecurityUtils.tryGetUserId());
            record.setModule(fieldAudit.module());
            record.setEntityName(fieldAudit.entity().getSimpleName());
            record.setEntityId(id);
            record.setAction(fieldAudit.action());
            record.setChangedFields(FieldDiffUtils.toJson(changes, objectMapper));
            record.setBeforeData(truncate(FieldDiffUtils.toJson(before, objectMapper)));
            record.setAfterData(truncate(FieldDiffUtils.toJson(after, objectMapper)));
            record.setStatus(error == null ? STATUS_SUCCESS : STATUS_FAILURE);
            record.setCreatedAt(LocalDateTime.now());
            fieldAuditLogMapper.insert(record);
            businessMetrics.fieldAuditWritten();
        } catch (RuntimeException exception) {
            log.warn("Failed to write field audit log", exception);
        }
    }

    private Object selectById(Class<?> entityClass, Long id) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            return null;
        }
        // JdbcTemplate 绑定当前事务连接：事务内业务未提交的更新在 after 快照中可见，
        // 保证 diff 基于真实变更而非过期状态。
        // 租户表白名单表必须显式带租户条件——JdbcTemplate 直查绕开
        // MyBatis-Plus 租户拦截器，否则租户 A 拿租户 B 记录 ID 调 update
        // （业务更新被拦截器挡掉、0 行生效），before/after 快照会把租户 B 行全字段
        // JSON 写入租户 A 自己的 sys_field_audit_log，构成跨租户外带通道。
        // 白名单与租户拦截器同源（MybatisPlusConfig.TENANT_TABLES），避免两处判定分叉。
        boolean tenantScoped = MybatisPlusConfig.TENANT_TABLES
                .contains(tableInfo.getTableName().toLowerCase(Locale.ROOT));
        String sql;
        Object[] args;
        if (tenantScoped) {
            sql = "SELECT * FROM " + tableInfo.getTableName()
                    + " WHERE " + tableInfo.getKeyColumn() + " = ? AND "
                    + MybatisPlusConfig.TENANT_ID_COLUMN + " = ?";
            args = new Object[]{id, TenantContext.getTenantId()};
        } else {
            sql = "SELECT * FROM " + tableInfo.getTableName()
                    + " WHERE " + tableInfo.getKeyColumn() + " = ?";
            args = new Object[]{id};
        }
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            try {
                Object entity = entityClass.getDeclaredConstructor().newInstance();
                BeanWrapperImpl wrapper = new BeanWrapperImpl(entity);
                wrapper.setPropertyValue(tableInfo.getKeyProperty(), convert(rs.getObject(tableInfo.getKeyColumn())));
                for (TableFieldInfo field : tableInfo.getFieldList()) {
                    Object value = rs.getObject(field.getColumn());
                    if (value != null) {
                        wrapper.setPropertyValue(field.getProperty(), convert(value));
                    }
                }
                return entity;
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }, args);
    }

    /** JDBC 时间戳转 LocalDateTime，其余类型由 BeanWrapper 自动转换 */
    private Object convert(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value;
    }

    /** 从方法参数提取被审计记录主键：优先实体参数（@TableId），其次含 getId() 的 DTO。 */
    private Long extractId(Class<?> entityClass, Object[] args) {
        for (Object arg : args) {
            if (arg == null || isRequestType(arg)) {
                continue;
            }
            if (entityClass.isInstance(arg)) {
                Long id = readTableId(arg);
                if (id != null) {
                    return id;
                }
            }
        }
        for (Object arg : args) {
            if (arg == null || isRequestType(arg)) {
                continue;
            }
            try {
                Method getter = arg.getClass().getMethod("getId");
                Object id = getter.invoke(arg);
                if (id instanceof Number number) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // 无 getId() 的参数跳过
            }
        }
        return null;
    }

    private Long readTableId(Object entity) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        if (tableInfo == null || tableInfo.getKeyProperty() == null) {
            return null;
        }
        try {
            Method getter = entity.getClass().getMethod("get" + capitalize(tableInfo.getKeyProperty()));
            Object id = getter.invoke(entity);
            return id instanceof Number number ? number.longValue() : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object firstEntityArg(Class<?> entityClass, Object[] args) {
        for (Object arg : args) {
            if (arg != null && !isRequestType(arg) && entityClass.isInstance(arg)) {
                return arg;
            }
        }
        return null;
    }

    private boolean isRequestType(Object arg) {
        return arg instanceof ServletRequest || arg instanceof ServletResponse;
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_SNAPSHOT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SNAPSHOT_LENGTH);
    }
}
