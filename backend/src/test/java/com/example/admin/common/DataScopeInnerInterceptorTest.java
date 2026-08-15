package com.example.admin.common;

import com.example.admin.module.system.DataPermissionRuleResolver;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeInnerInterceptorTest {

    @Mock
    private Executor executor;

    @Mock
    private MappedStatement mappedStatement;

    @Mock
    private DataPermissionRuleResolver ruleResolver;

    @AfterEach
    void clearDataScope() {
        DataScopeContext.clear();
    }

    private BoundSql run(DataScopeContext.Filter filter, String sql) {
        DataScopeContext.set(filter);
        BoundSql boundSql = new BoundSql(new Configuration(), sql, List.<ParameterMapping>of(), null);
        new DataScopeInnerInterceptor(ruleResolver).beforeQuery(
                executor, mappedStatement, null, RowBounds.DEFAULT, null, boundSql);
        return boundSql;
    }

    @Test
    void leavesSqlUntouchedWhenTableNotConfigured() {
        when(ruleResolver.resolve("sys_bar")).thenReturn(null);
        BoundSql boundSql = run(new DataScopeContext.Filter(
                List.of(1L), List.of(), Set.of("sys_bar"), false), "SELECT * FROM sys_bar");

        assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM sys_bar");
    }

    @Test
    void rewritesUserColumnTableWithConfiguredColumn() {
        // 配置化验证：非内置表 sys_foo 配置 user_column=owner_id 后即生效，无需改代码
        when(ruleResolver.resolve("sys_foo"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_foo", "owner_id", null));
        BoundSql boundSql = run(new DataScopeContext.Filter(
                List.of(1L, 2L), List.of(), Set.of("sys_foo"), false), "SELECT * FROM sys_foo t");

        assertThat(boundSql.getSql()).contains("t.owner_id IN (1, 2)");
    }

    @Test
    void rewritesUsernameColumnTableWithConfiguredColumn() {
        when(ruleResolver.resolve("sys_login_log"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_login_log", null, "username"));
        BoundSql boundSql = run(new DataScopeContext.Filter(
                List.of(), List.of("alice"), Set.of("sys_login_log"), false), "SELECT * FROM sys_login_log");

        assertThat(boundSql.getSql()).contains("sys_login_log.username IN ('alice')");
    }

    @Test
    void leavesSqlUntouchedWhenRuleNeedsUserIdsButFilterIsNull() {
        // 规则配置了 user_column，但当前用户可见集合为 null（如管理员不设限）——不追加条件
        when(ruleResolver.resolve("sys_user"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_user", "id", null));
        BoundSql boundSql = run(new DataScopeContext.Filter(
                null, null, Set.of("sys_user"), false), "SELECT * FROM sys_user");

        assertThat(boundSql.getSql()).isEqualTo("SELECT * FROM sys_user");
    }

    @Test
    void rewritesToImpossibleWhenFilterEmpty() {
        when(ruleResolver.resolve("sys_user"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_user", "id", null));
        BoundSql boundSql = run(new DataScopeContext.Filter(
                List.of(), List.of(), Set.of("sys_user"), true), "SELECT * FROM sys_user u");

        assertThat(boundSql.getSql()).contains("u.id = -1");
    }

    // ---------- 批8a：审计日志两表注册 user_id 关联列 ----------

    @Test
    void rewritesAuditLogTableWithUserIdColumn() {
        when(ruleResolver.resolve("sys_audit_log"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_audit_log", "user_id", null));
        BoundSql boundSql = run(new DataScopeContext.Filter(
                List.of(3L, 7L), List.of(), Set.of("sys_audit_log"), false), "SELECT * FROM sys_audit_log");

        assertThat(boundSql.getSql()).contains("sys_audit_log.user_id IN (3, 7)");
    }

    @Test
    void rewritesFieldAuditLogTableWithUserIdColumn() {
        when(ruleResolver.resolve("sys_field_audit_log"))
                .thenReturn(new DataPermissionRuleResolver.Rule("sys_field_audit_log", "user_id", null));
        BoundSql boundSql = run(new DataScopeContext.Filter(
                List.of(3L, 7L), List.of(), Set.of("sys_field_audit_log"), false), "SELECT * FROM sys_field_audit_log");

        assertThat(boundSql.getSql()).contains("sys_field_audit_log.user_id IN (3, 7)");
    }
}
