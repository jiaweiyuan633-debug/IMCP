package cn.admin.scaffold.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.DataScopeContext;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.DataPermissionRuleResolver;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回归测试：分页 COUNT 查询必须携带租户与数据权限条件。
 *
 * <p>背景：PaginationInnerInterceptor 的 COUNT 查询在 willDoQuery 中直接经真实 Executor
 * 执行（不重入拦截器链），COUNT SQL 只取自「当前时刻」的 boundSql。因此条件注入类拦截器
 * （租户、数据权限）必须排在分页之前，否则 COUNT 会统计到全租户/越权行。
 * 本测试用真实拦截器链 + mock Executor 驱动一次分页查询，捕获 COUNT SQL 断言。
 */
class MybatisPlusInterceptorConfigTest {

    private Configuration configuration;
    private Executor executor;
    private MappedStatement ms;
    private MybatisPlusInterceptor interceptor;
    private final List<String> capturedCountSql = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws java.sql.SQLException {
        configuration = new Configuration();
        executor = mock(Executor.class);
        ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);
        when(ms.getId()).thenReturn("cn.admin.scaffold.it.PageTenantMapper.selectPage");
        when(ms.getConfiguration()).thenReturn(configuration);
        // MappedStatement.Builder.build() 断言 sqlSource != null（count Ms 仅用其承载 id，不执行）
        when(ms.getSqlSource()).thenReturn(mock(SqlSource.class));

        DataPermissionRuleResolver resolver = mock(DataPermissionRuleResolver.class);
        when(resolver.resolve("sys_user"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_user", "id", null));
        ObjectProvider<DataPermissionRuleResolver> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(resolver);

        interceptor = new MybatisPlusConfig(provider).mybatisPlusInterceptor();

        when(executor.createCacheKey(any(), any(), any(), any())).thenReturn(new CacheKey());
        when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), any(),
                any(CacheKey.class), any(BoundSql.class))).thenAnswer(this::answerQuery);
    }

    @AfterEach
    void clearContexts() {
        TenantContext.clear();
        DataScopeContext.clear();
    }

    /** 区分 COUNT 查询（_mpCount 后缀）与真实分页查询，仅捕获 COUNT SQL。 */
    private List<?> answerQuery(InvocationOnMock invocation) {
        MappedStatement invokedMs = invocation.getArgument(0);
        if (invokedMs.getId().endsWith("_mpCount")) {
            capturedCountSql.add(invocation.getArgument(5, BoundSql.class).getSql());
            // total=20，current=1、size=10 时 pages=2，保证 continuePage=true 走完整链路
            return List.of(20L);
        }
        return List.of();
    }

    private void driveChain(BoundSql boundSql) throws Throwable {
        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class);
        Page<Object> page = new Page<>(1, 10);
        interceptor.intercept(new Invocation(executor, method,
                new Object[]{ms, page, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER, null, boundSql}));
    }

    private BoundSql pageSql() {
        return new BoundSql(configuration,
                "SELECT id, username FROM sys_user WHERE status = ?",
                List.of(new ParameterMapping.Builder(configuration, "status", Integer.class).build()),
                new Object[]{1});
    }

    @Test
    void countQueryCarriesTenantAndDataScopeConditions() throws Throwable {
        TenantContext.setTenantId(2L);
        DataScopeContext.set(new DataScopeContext.Filter(List.of(1L, 2L), List.of(), Set.of("sys_user"), false));
        BoundSql boundSql = pageSql();

        driveChain(boundSql);

        // COUNT 必须包含租户条件与数据权限条件（条件注入发生在分页之前）
        assertThat(capturedCountSql).hasSize(1);
        assertThat(capturedCountSql.get(0))
                .as("COUNT SQL 必须携带租户条件，不得统计全租户行")
                .contains("tenant_id = 2")
                .as("COUNT SQL 必须携带数据权限条件")
                .contains("sys_user.id IN");
        // 最终分页 SQL 同样携带条件并追加 LIMIT
        assertThat(boundSql.getSql())
                .contains("tenant_id = 2")
                .contains("sys_user.id IN")
                .contains("LIMIT");
    }

    @Test
    void countQueryCarriesTenantConditionWithoutDataScope() throws Throwable {
        TenantContext.setTenantId(3L);
        BoundSql boundSql = pageSql();

        driveChain(boundSql);

        assertThat(capturedCountSql).hasSize(1);
        assertThat(capturedCountSql.get(0))
                .contains("tenant_id = 3")
                .doesNotContain("sys_user.id IN");
    }

    @Test
    void mcpServerSqlCarriesTenantCondition() throws Throwable {
        // sys_mcp_server 补进租户名单——他租户 MCP 服务（含 authToken）不能被
        // 跨租户列出/加载/修改/删除，外部工具调用不得越权携带他租户连接凭据
        TenantContext.setTenantId(5L);
        BoundSql boundSql = new BoundSql(configuration,
                "SELECT id, name, url, auth_token FROM sys_mcp_server WHERE enabled = ?",
                List.of(new ParameterMapping.Builder(configuration, "enabled", Integer.class).build()),
                new Object[]{1});

        driveChain(boundSql);

        assertThat(boundSql.getSql())
                .contains("tenant_id = 5")
                .doesNotContain("sys_user.id IN");
    }

    @Test
    void tenantTablesIncludeSysMcpServer() {
        // 白名单含 sys_mcp_server，防名单回退（漏加即回归跨租户越权）
        assertThat(MybatisPlusConfig.TENANT_TABLES).contains("sys_mcp_server");
    }

    @Test
    void conditionInjectorsRunBeforePagination() {
        assertThat(interceptor.getInterceptors())
                .extracting(i -> i.getClass().getSimpleName())
                .containsExactly("TenantLineInnerInterceptor", "DataScopeInnerInterceptor",
                        "PaginationInnerInterceptor", "OptimisticLockerInnerInterceptor");
    }
}
