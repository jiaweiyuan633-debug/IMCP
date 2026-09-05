package cn.admin.scaffold.module.system;

import cn.admin.scaffold.module.system.entity.SysApiPermDO;
import cn.admin.scaffold.module.system.mapper.SysApiPermMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiPermRegistryTest {

    private SysApiPermMapper apiPermMapper;
    private ApiPermRegistry registry;

    @BeforeEach
    void setUp() {
        apiPermMapper = mock(SysApiPermMapper.class);
        registry = new ApiPermRegistry(apiPermMapper);
    }

    private SysApiPermDO rule(Long id, String method, String path, String perm, int enabled) {
        SysApiPermDO rule = new SysApiPermDO();
        rule.setId(id);
        rule.setMethod(method);
        rule.setPathPattern(path);
        rule.setPermCode(perm);
        rule.setEnabled(enabled);
        return rule;
    }

    private void loadRules(SysApiPermDO... rules) {
        when(apiPermMapper.selectList(any())).thenReturn(List.of(rules));
        registry.reload();
    }

    @Test
    void resolveReturnsPermForMatchingMethodAndPath() {
        loadRules(rule(1L, "POST", "/api/system/user/**", "system:user:add", 1));

        assertThat(registry.resolve("POST", "/api/system/user/1")).isEqualTo("system:user:add");
        assertThat(registry.resolve("post", "/api/system/user/1")).isEqualTo("system:user:add");
    }

    @Test
    void resolveReturnsNullWhenMethodMismatch() {
        loadRules(rule(1L, "POST", "/api/system/user/**", "system:user:add", 1));

        assertThat(registry.resolve("GET", "/api/system/user/1")).isNull();
    }

    @Test
    void resolveReturnsNullWhenPathMismatch() {
        loadRules(rule(1L, "POST", "/api/system/user/**", "system:user:add", 1));

        assertThat(registry.resolve("POST", "/api/system/role/1")).isNull();
        assertThat(registry.resolve("POST", "/api/other")).isNull();
    }

    @Test
    void wildcardMethodMatchesAnyHttpMethod() {
        loadRules(rule(1L, "*", "/api/system/dict/type", "system:dict:add", 1));

        assertThat(registry.resolve("GET", "/api/system/dict/type")).isEqualTo("system:dict:add");
        assertThat(registry.resolve("DELETE", "/api/system/dict/type")).isEqualTo("system:dict:add");
    }

    @Test
    void reloadOnlyLoadsEnabledRules() {
        loadRules(
                rule(1L, "POST", "/api/system/user/**", "system:user:add", 1),
                rule(2L, "DELETE", "/api/system/user/**", "system:user:delete", 0));

        assertThat(registry.resolve("DELETE", "/api/system/user/1")).isNull();
        assertThat(registry.resolve("POST", "/api/system/user/1")).isEqualTo("system:user:add");
    }

    @Test
    void reloadFailureKeepsPreviousSnapshot() {
        loadRules(rule(1L, "POST", "/api/system/user/**", "system:user:add", 1));
        when(apiPermMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        registry.reload();

        assertThat(registry.resolve("POST", "/api/system/user/1")).isEqualTo("system:user:add");
    }
}
