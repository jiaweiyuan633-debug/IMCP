package com.example.admin.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级审计标注。
 *
 * <p>标注在 Service 方法上，由 {@code FieldAuditAspect} 拦截：
 * 方法执行前按参数中的主键读取旧快照，执行后（或从参数/数据库）读取新快照，
 * 计算字段级 diff 并写入 {@code sys_field_audit_log}。审计逻辑失败不影响业务主流程。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldAudit {

    /** 被审计实体类（MyBatis-Plus DO），用于反射字段与查询旧快照 */
    Class<?> entity();

    /** 变更动作：CREATE / UPDATE / DELETE */
    String action() default "UPDATE";

    /** 业务模块名 */
    String module() default "";
}
