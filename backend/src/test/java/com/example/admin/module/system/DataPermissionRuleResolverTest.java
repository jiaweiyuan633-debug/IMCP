package com.example.admin.module.system;

import com.example.admin.module.system.entity.SysDataPermissionDO;
import com.example.admin.module.system.mapper.SysDataPermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPermissionRuleResolverTest {

    @Mock
    private SysDataPermissionMapper mapper;

    private DataPermissionRuleResolver resolver;

    private SysDataPermissionDO row(String table, String userColumn, String usernameColumn) {
        SysDataPermissionDO row = new SysDataPermissionDO();
        row.setTableName(table);
        row.setUserColumn(userColumn);
        row.setUsernameColumn(usernameColumn);
        row.setEnabled(1);
        return row;
    }

    @BeforeEach
    void setUp() {
        resolver = new DataPermissionRuleResolver(mapper);
    }

    @Test
    void resolvesConfiguredRulesCaseInsensitively() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row("sys_user", "id", null),
                row("sys_login_log", null, "username")));

        resolver.reload();

        assertThat(resolver.resolve("sys_user").userColumn()).isEqualTo("id");
        assertThat(resolver.resolve("SYS_USER").userColumn()).isEqualTo("id");
        assertThat(resolver.resolve("sys_login_log").usernameColumn()).isEqualTo("username");
    }

    @Test
    void returnsNullForUnconfiguredTable() {
        when(mapper.selectList(any())).thenReturn(List.of(row("sys_user", "id", null)));
        resolver.reload();

        assertThat(resolver.resolve("sys_foo")).isNull();
    }

    @Test
    void keepsOldCacheWhenReloadFails() {
        when(mapper.selectList(any()))
                .thenReturn(List.of(row("sys_user", "id", null)))
                .thenThrow(new RuntimeException("db down"));
        resolver.reload();
        assertThat(resolver.resolve("sys_user")).isNotNull();

        // 第二次加载失败：保留旧缓存，避免权限短暂失效
        resolver.reload();
        assertThat(resolver.resolve("sys_user").userColumn()).isEqualTo("id");
    }
}
