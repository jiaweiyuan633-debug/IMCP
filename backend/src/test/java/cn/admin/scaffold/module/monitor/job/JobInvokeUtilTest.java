package cn.admin.scaffold.module.monitor.job;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobInvokeUtilTest {

    @Test
    void rejectsInvalidInvokeTarget() {
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.invoke("missing-dot"));
    }

    @Test
    void rejectsFormatWithNonAlphanumericChars() {
        // invokeTarget 仅允许字母数字下划线——路径穿越/类名注入/空格一律拒绝
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("a/b.c"));
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("a.b;DROP"));
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("a b.c"));
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("a.b.c")); // 三段
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("")); // 空串
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate(null));
    }

    @Test
    void rejectsUnregisteredBeanMethod() {
        // 未在注册表登记的方法一律不可触发——即使 bean 存在且有无参方法
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("demoTask.shutdown"));
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("scheduler.shutdown"));
        assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.validate("tokenService.evictAllPermissions"));
    }

    @Test
    void acceptsRegisteredBeanMethod() {
        // 显式登记的方法放行（demoTask.runDemo 为内置示例）
        assertDoesNotThrow(() -> JobInvokeUtil.validate("demoTask.runDemo"));
    }

    @Test
    void registerAddsAllowedMethod() {
        JobInvokeUtil.register("testTask", Set.of("run"));
        assertDoesNotThrow(() -> JobInvokeUtil.validate("testTask.run"));
    }
}
