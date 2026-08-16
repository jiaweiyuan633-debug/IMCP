package com.example.admin;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * 集成测试的 Docker 前置条件：Docker 不可用时把整个测试类标记为「跳过」（disabled），
 * 而不是让 {@link AbstractIntegrationTest} 的 {@code @SpringBootTest} 尝试加载容器上下文
 * 后报 context 加载失败（R4-1.36 批次9 修复）。
 *
 * <p>此前跳依赖 {@code @DynamicPropertySource} 内的 {@code Assumptions}，但该异常发生在
 * Spring TestContext 加载阶段，会被当作 context 加载失败记为 error（而非 skipped），
 * 与 pom 中「无 Docker 时自动跳过」的注释不符；本地无 Docker 时 8 个 IT 类全部 error，
 * 阻塞 {@code mvn verify}（JaCoCo 门禁绑定在 verify 阶段）。
 *
 * <p>ExecutionCondition 在 JUnit 收集阶段、context 加载之前求值，disabled 时 Spring 上下文
 * 不会被创建；Docker 检测通过但容器启动失败的情况仍由基类静态块 + assumption 兜底跳过。
 */
public class DockerExecutionCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker 可用，运行集成测试");
            }
        } catch (Throwable ignored) {
            // 检测本身抛异常（守护进程未响应、API 协商失败等）一律按不可用处理
        }
        return ConditionEvaluationResult.disabled("Docker 不可用，跳过集成测试");
    }
}
