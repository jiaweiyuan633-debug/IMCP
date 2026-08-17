package com.example.admin;

import com.example.admin.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;

/**
 * 集成测试基类（批次1 可靠性纵深：Testcontainers 补测试金字塔中层）。
 *
 * <p>JVM 级单例 MySQL + Redis 容器，Flyway 自动在容器库执行 V1-V52 迁移；
 * 数据源与 Redis 连接通过 {@code @DynamicPropertySource} 指向容器，
 * 使 Spring Data Redis 与 Redisson 均连容器（避免 Redisson 读静态 localhost 配置连错）。
 *
 * <p>容器在静态块手动启动（而非 {@code @Container} 生命周期）：整个测试 JVM 只启动一次，
 * 规避 Windows Docker Desktop 每个测试类反复创建/销毁容器时端口映射偶发 Connection refused；
 * 端口不变使 8 个 IT 类共享同一 Spring 上下文（Spring TestContext 按属性值缓存），提速约 8 倍。
 * 测试间共享库，用例须自建自清数据或使用不同唯一键。
 *
 * <p>无 Docker 环境自动跳过（R4-1.36 起由 {@link DockerExecutionCondition} 在收集阶段整体
 * disabled，Docker 可用但容器启动失败时再由 {@code @DynamicPropertySource} 的 assumption
 * 兜底跳过），不阻塞纯单元测试运行，也不阻塞 {@code mvn verify} 的 JaCoCo 门禁。
 *
 * <p>环境适配（2026-08 修复）：
 * <ol>
 *   <li>必须用 MOCK Web 环境而非 NONE：knife4j.enable=true 时 Knife4jAutoConfiguration
 *       需要 SpringDocConfigProperties，而 SpringDocAutoConfiguration 仅在 Servlet 环境装配，
 *       NONE 下上下文加载必失败。</li>
 *   <li>测试 Redis 必须配密码：redisson-spring-boot-starter 3.30 自动配置会把空串密码也
 *       发送 AUTH，无密码 Redis 7 直接拒绝（ERR AUTH called without password configured）。</li>
 *   <li>Windows + Docker Desktop 29.x：npipe bootstrap redirect + Docker API≥v1.44，
 *       docker-java 默认协商 v1.32 且不跟随重定向 → 本机需 ~/.docker-java.properties
 *       固定 api.version=1.44（仅本机生效，不影响 CI 的 Unix socket）。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("dev")
@ExtendWith(DockerExecutionCondition.class)
public abstract class AbstractIntegrationTest {

    // 容器为 JVM 级单例，生命周期由 JVM 退出时的 ryuk 清理，非方法内资源，压制 Resource leak 警告
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("admin_scaffold")
            .withUsername("root")
            .withPassword("test");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server --requirepass test");

    static {
        // JVM 级单例：整个测试 JVM 只启动一次容器。Docker 不可用或启动失败时静默降级，
        // 由 @DynamicPropertySource 中的 assumption 将整个测试类标记为跳过。
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                MYSQL.start();
                REDIS.start();
            }
        } catch (RuntimeException ignored) {
            // 容器启动失败不阻塞构建，交给 assumption 跳过
        }
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        Assumptions.assumeTrue(MYSQL.isRunning() && REDIS.isRunning(),
                "Docker 不可用或容器启动失败，跳过集成测试");
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
        registry.add("spring.data.redis.password", () -> "test");
    }

    /**
     * 集成测试以管理员视角运行：统一注入 admin 登录上下文。
     *
     * <p>R4-1.37 起部分 Service 方法（如 FormInstanceService.page / ImportExportJobService.page）
     * 增加 {@code @DataScope} 注解，切面经 {@code SecurityUtils.getLoginUser()} 取当前用户判定
     * 是否 admin 短路——此前 IT 未注入登录上下文，调用带 @DataScope 的方法即抛「未登录或登录已
     * 过期」，FormInstanceServiceIT / ImportExportJobServiceIT 从批10起实际处于失效状态（存量
     * 缺陷，批次1 门禁修复）。admin 角色短路行级过滤，与既有 IT 的全量断言语义一致。
     */
    @BeforeEach
    void setAdminSecurityContext() {
        LoginUser loginUser = LoginUser.builder()
                .userId(1L)
                .deptId(1L)
                .username("admin")
                .nickname("系统管理员")
                .roles(List.of("admin"))
                .perms(List.of("*"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
