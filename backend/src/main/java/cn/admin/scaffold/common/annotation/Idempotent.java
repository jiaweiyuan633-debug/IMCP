package cn.admin.scaffold.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等保护：同一幂等键在窗口期内仅首次执行。
 *
 * <p>实现依赖 Redis SETNX（见 {@code IdempotentAspect}），作用于 HTTP 写接口。
 * 典型用法：
 * <pre>{@code
 * @Idempotent(key = "#request.code", expireSeconds = 30)
 * public Result<Long> create(@RequestBody DeviceSaveRequest request) { ... }
 * }</pre>
 *
 * <p>两种语义：
 * <ul>
 *   <li>默认（returnCached=false）：窗口期内重复请求抛「请勿重复提交」；执行失败会释放键，允许重试。</li>
 *   <li>returnCached=true：窗口期内重复请求直接返回首次结果（幂等返回），首次执行结束后键保留到 TTL。</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 幂等键 SpEL 表达式：引用方法参数（如 {@code #request.code}）。留空时用「类.方法 + 参数 JSON」自动生成。 */
    String key() default "";

    /** 幂等窗口秒数。 */
    long expireSeconds() default 60;

    /** true=重复请求返回首次结果；false=重复请求抛「请勿重复提交」。 */
    boolean returnCached() default false;
}
