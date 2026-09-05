package cn.admin.scaffold.common.aspect;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.admin.scaffold.common.BusinessMetrics;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.common.annotation.FieldAudit;
import cn.admin.scaffold.module.system.entity.SysNoticeDO;
import cn.admin.scaffold.module.system.mapper.SysFieldAuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldAuditAspectTest {

    // 切面 selectById 依赖 MyBatis-Plus TableInfo（表名/主键/字段），需先注册实体
    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysNoticeDO.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), NonTenantEntity.class);
    }

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SysFieldAuditLogMapper fieldAuditLogMapper = mock(SysFieldAuditLogMapper.class);
    private final BusinessMetrics businessMetrics = mock(BusinessMetrics.class);
    private final FieldAuditAspect aspect =
            new FieldAuditAspect(fieldAuditLogMapper, jdbcTemplate, new ObjectMapper(), businessMetrics);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 租户表快照直查必须带当前租户条件。传的 ID 可能属于其他租户
     * （业务更新会被租户拦截器挡掉、0 行生效），若快照不加 tenant_id 过滤，
     * 他人租户行全字段会经 before/after 快照泄入本租户审计日志。
     */
    @Test
    void snapshotReadsCarryTenantConditionForTenantTables() throws Throwable {
        TenantContext.setTenantId(7L);
        SysNoticeDO notice = new SysNoticeDO();
        notice.setId(100L);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{notice});
        when(joinPoint.proceed()).thenReturn(null);

        aspect.around(joinPoint, auditAnnotation(SysNoticeDO.class));

        // before + after 两次快照直查均带租户条件，参数为 (id, 当前租户)
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2))
                .query(sqlCaptor.capture(), any(ResultSetExtractor.class), argsCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql)
                        .contains("FROM sys_notice WHERE id = ? AND tenant_id = ?"));
        assertThat(argsCaptor.getAllValues())
                .allSatisfy(args -> assertThat(args).containsExactly(100L, 7L));
    }

    /** 非租户表不加租户条件（回归保护：修复不得影响白名单外的全局表）。 */
    @Test
    void snapshotReadsSkipTenantConditionForNonTenantTables() throws Throwable {
        TenantContext.setTenantId(7L);
        NonTenantEntity entity = new NonTenantEntity();
        entity.setId(5L);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{entity});
        when(joinPoint.proceed()).thenReturn(null);

        aspect.around(joinPoint, auditAnnotation(NonTenantEntity.class));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2))
                .query(sqlCaptor.capture(), any(ResultSetExtractor.class), argsCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql)
                        .contains("FROM demo_non_tenant WHERE id = ?")
                        .doesNotContain("tenant_id"));
        assertThat(argsCaptor.getAllValues())
                .allSatisfy(args -> assertThat(args).containsExactly(5L));
    }

    private FieldAudit auditAnnotation(Class<?> entityClass) {
        FieldAudit audit = mock(FieldAudit.class);
        doReturn(entityClass).when(audit).entity();
        when(audit.action()).thenReturn("UPDATE");
        when(audit.module()).thenReturn("test");
        return audit;
    }

    /** 非租户表实体：验证白名单外表不受租户条件影响。 */
    @Data
    @TableName("demo_non_tenant")
    public static class NonTenantEntity {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String name;
    }
}
