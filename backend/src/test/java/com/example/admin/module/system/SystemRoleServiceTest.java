package com.example.admin.module.system;

import com.example.admin.module.system.mapper.SysRoleDeptMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysRoleMenuMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.security.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemRoleServiceTest {

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysRoleMenuMapper roleMenuMapper;

    @Mock
    private SysRoleDeptMapper roleDeptMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private SystemRoleService roleService;

    @Test
    void assignMenusEvictsOnlyRoleUsers() {
        when(userRoleMapper.selectUserIdsByRoleIds(List.of(5L))).thenReturn(List.of(1L, 2L));

        roleService.assignMenus(5L, List.of(10L, 11L));

        verify(roleMenuMapper).deleteByRoleId(5L);
        verify(roleMenuMapper).insertBatch(5L, List.of(10L, 11L));
        verify(tokenService).evictPermissionsByUserIdsAfterCommit(List.of(1L, 2L));
    }

    @Test
    void assignMenusClearAlsoEvicts() {
        when(userRoleMapper.selectUserIdsByRoleIds(List.of(5L))).thenReturn(List.of(9L));

        // 清空授权：menuIds 为空也必须失效权限缓存，否则用户仍持旧权限
        roleService.assignMenus(5L, List.of());

        verify(roleMenuMapper).deleteByRoleId(5L);
        verify(tokenService).evictPermissionsByUserIdsAfterCommit(List.of(9L));
    }

    @Test
    void assignMenusWithoutBindUsersSkipsEviction() {
        when(userRoleMapper.selectUserIdsByRoleIds(List.of(5L))).thenReturn(List.of());

        roleService.assignMenus(5L, List.of(1L));

        verify(roleMenuMapper).insertBatch(eq(5L), eq(List.of(1L)));
        verify(tokenService).evictPermissionsByUserIdsAfterCommit(List.of());
    }

    @Test
    void deleteRoleEvictsBoundUsers() {
        // R4-1.31：删除角色后拥有者仍持旧权限（缓存 TTL 30 分钟）是缺陷——须失效绑定用户权限缓存
        when(userRoleMapper.selectUserIdsByRoleIds(List.of(3L))).thenReturn(List.of(4L, 5L));

        roleService.delete(3L);

        verify(roleMapper).deleteById(3L);
        verify(roleMenuMapper).deleteByRoleId(3L);
        verify(roleDeptMapper).deleteByRoleId(3L);
        verify(tokenService).evictPermissionsByUserIdsAfterCommit(List.of(4L, 5L));
    }

    @Test
    void deleteRoleWithoutBindUsersSkipsEviction() {
        when(userRoleMapper.selectUserIdsByRoleIds(List.of(3L))).thenReturn(List.of());

        roleService.delete(3L);

        verify(tokenService).evictPermissionsByUserIdsAfterCommit(List.of());
    }
}
