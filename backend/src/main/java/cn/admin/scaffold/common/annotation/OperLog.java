package cn.admin.scaffold.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    String module() default "";

    String action() default "";

    /**
     * 额外脱敏字段：入参/结果序列化后，命中这些键名的值整体打码为 ******。
     * 用于发送类操作（渠道消息/站内信/模板渲染）的正文与参数脱敏——content/target 等键
     * 不在 LogMaskUtils 黑名单（黑名单加 content 会误伤公告/通知等本应审计留痕的正文），
     * 故按注解精确声明需脱敏的字段。
     */
    String[] maskFields() default {};
}

