package com.example.admin.module.system;

import com.example.admin.common.TenantContext;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.mapper.SysDeptMapper;
import com.example.admin.module.system.mapper.SysPostMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysTenantMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysUserPostMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysUserPostMapper userPostMapper;

    @Mock
    private SysDeptMapper deptMapper;

    @Mock
    private SysPostMapper postMapper;

    @Mock
    private DataScopeHelper dataScopeHelper;

    @Mock
    private SysConfigMapper configMapper;

    @Mock
    private SysTenantMapper tenantMapper;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SystemUserService userService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createEncodesPasswordAndAssignsRolesAndPosts() {
        TenantContext.setTenantId(1L);
        when(userMapper.exists(any())).thenReturn(false);
        when(tenantMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("abc12345")).thenReturn("encoded");
        doAnswer(invocation -> {
            SysUserDO user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        }).when(userMapper).insert(any(SysUserDO.class));

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername("alice");
        request.setPassword("abc12345");
        request.setNickname("Alice");
        request.setRoleIds(List.of(2L));
        request.setPostIds(List.of(3L));

        Long id = userService.create(request);

        assertEquals(10L, id);
        verify(passwordEncoder).encode("abc12345");
        verify(userRoleMapper).insert(eq(10L), eq(2L));
        verify(userPostMapper).insert(eq(10L), eq(3L));
    }

    @Test
    void assignRolesEvictsUserPermissions() {
        userService.assignRoles(7L, List.of(2L, 3L));

        verify(userRoleMapper).deleteByUserId(7L);
        verify(userRoleMapper).insert(eq(7L), eq(2L));
        verify(userRoleMapper).insert(eq(7L), eq(3L));
        verify(tokenService).evictUserPermissionsAfterCommit(7L);
    }

    @Test
    void assignRolesClearAlsoEvicts() {
        // 清空角色必须失效权限缓存，否则用户仍持旧权限直到 TTL
        userService.assignRoles(7L, List.of());

        verify(userRoleMapper).deleteByUserId(7L);
        verify(tokenService).evictUserPermissionsAfterCommit(7L);
    }

    @Test
    void deleteUserEvictsPermissions() {
        // R4-1.31：删除用户后残留权限缓存（TTL 30 分钟）无意义且占位，一并失效
        userService.delete(7L);

        verify(userMapper).deleteById(7L);
        verify(userRoleMapper).deleteByUserId(7L);
        verify(userPostMapper).deleteByUserId(7L);
        verify(tokenService).evictUserPermissionsAfterCommit(7L);
    }

    @Test
    void updateStatusEvictsPermissions() {
        // R4-1.31：禁用/重新启用均清除权限缓存——重新启用后若缓存为禁用前旧快照会残留旧权限
        SysUserDO user = new SysUserDO();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);

        userService.updateStatus(7L, 0);

        verify(userMapper).updateById(user);
        verify(tokenService).evictUserPermissionsAfterCommit(7L);
    }
}
