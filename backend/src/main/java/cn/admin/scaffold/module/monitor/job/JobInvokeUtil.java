package cn.admin.scaffold.module.monitor.job;

import cn.admin.scaffold.common.SpringContextHolder;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Quartz 任务反射调用工具（安全加固）。
 *
 * <p>原实现 {@code getBean(parts[0]).getClass().getMethod(parts[1])} 无任何目标白名单，
 * 可命中任意 Spring Bean 的任意 public 无参方法（如 {@code scheduler.shutdown()} 使全集群
 * 调度停摆、{@code tokenService.evictAllPermissions()} 清空权限缓存 DoS）。修复：
 * <ol>
 *   <li>invokeTarget 格式白名单：{@code beanName.method} 仅允许字母数字下划线，拒绝路径穿越/
 *       类名注入（原 RuoYi 同款正则）；</li>
 *   <li>可调用方法注册表：仅显式登记的 bean 方法可被调度触发（{@link #register}），
 *       未登记即抛 {@link IllegalArgumentException}——新增任务目标必须显式注册，杜绝
 *       反射调用任意 Bean 方法。</li>
 * </ol>
 */
public final class JobInvokeUtil {

    /** invokeTarget 格式白名单：beanName.method，仅字母数字下划线。 */
    private static final Pattern INVOKE_TARGET_PATTERN = Pattern.compile("\\A[a-zA-Z0-9_.]+\\z");

    /** 可调用方法注册表：bean 简单名 → 允许触发的无参方法名集合（类初始化时登记内置任务）。 */
    private static final Map<String, Set<String>> ALLOWED_METHODS = new ConcurrentHashMap<>();

    static {
        // 内置演示任务（迁移自 RuoYi demoTask，作为可调度目标示例）
        register("demoTask", Set.of("runDemo"));
    }

    private JobInvokeUtil() {
    }

    /** 显式登记可被 Quartz 调度的 bean 方法（安全边界：未登记的方法一律不可触发）。 */
    public static void register(String beanName, Set<String> methodNames) {
        ALLOWED_METHODS.put(beanName, methodNames);
    }

    /** 校验 invokeTarget 格式与白名单；合法返回 true，非法抛业务异常。 */
    public static void validate(String invokeTarget) {
        if (invokeTarget == null || !INVOKE_TARGET_PATTERN.matcher(invokeTarget).matches()) {
            throw new IllegalArgumentException("invokeTarget 格式非法，应为 beanName.method（仅字母数字下划线）");
        }
        String[] parts = invokeTarget.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invokeTarget 必须为 beanName.method 形式");
        }
        Set<String> allowed = ALLOWED_METHODS.get(parts[0]);
        if (allowed == null || !allowed.contains(parts[1])) {
            throw new IllegalArgumentException("不允许调度方法 " + invokeTarget
                    + "，请先在 JobInvokeUtil.register 显式登记");
        }
    }

    public static void invoke(String invokeTarget)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        validate(invokeTarget);
        String[] parts = invokeTarget.split("\\.");
        Object bean = SpringContextHolder.getBean(parts[0]);
        Method method = bean.getClass().getMethod(parts[1]);
        method.invoke(bean);
    }
}
