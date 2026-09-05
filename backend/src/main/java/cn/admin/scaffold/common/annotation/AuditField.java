package cn.admin.scaffold.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段审计元数据标注。
 *
 * <p>作用于实体字段，可选：{@link #label()} 提供字段中文名（缺省回退到字段名），
 * {@link #ignore()} 强制该字段不参与 diff（如动态维护的内部字段）。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditField {

    /** 字段中文名，用于审计展示 */
    String label() default "";

    /** 是否忽略该字段的变更记录 */
    boolean ignore() default false;
}
