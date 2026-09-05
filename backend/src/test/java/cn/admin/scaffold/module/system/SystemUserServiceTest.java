package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.dto.UserSaveRequest;
import cn.admin.scaffold.module.system.entity.SysTenantDO;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysConfigMapper;
import cn.admin.scaffold.module.system.mapper.SysDeptMapper;
import cn.admin.scaffold.module.system.mapper.SysPostMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysTenantMapper;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import cn.admin.scaffold.module.system.mapper.SysUserPostMapper;
import cn.admin.scaffold.module.system.mapper.SysUserRoleMapper;
import cn.admin.scaffold.security.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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
        when(passwordEncoder.encode("Abc@12345")).thenReturn("encoded");
        doAnswer(invocation -> {
            SysUserDO user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        }).when(userMapper).insert(any(SysUserDO.class));

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername("alice");
        request.setPassword("Abc@12345");
        request.setNickname("Alice");
        request.setRoleIds(List.of(2L));
        request.setPostIds(List.of(3L));

        Long id = userService.create(request);

        assertEquals(10L, id);
        verify(passwordEncoder).encode("Abc@12345");
        verify(userRoleMapper).insert(eq(10L), eq(2L));
        verify(userPostMapper).insert(eq(10L), eq(3L));
    }

    /**
     * 创建用户须持久化头像字段——此前 UserSaveRequest 缺 avatar 字段，
     * 前端已上传且 DB 列/VO 均存在，但 create 从未把值写库导致编辑后头像丢失。
     */
    @Test
    void createPersistsAvatar() {
        TenantContext.setTenantId(1L);
        when(userMapper.exists(any())).thenReturn(false);
        when(tenantMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Abc@12345")).thenReturn("encoded");
        when(userMapper.insert(any(SysUserDO.class))).thenReturn(1);

        UserSaveRequest request = new UserSaveRequest();
        request.setUsername("bob");
        request.setPassword("Abc@12345");
        request.setAvatar("/uploads/avatar.png");

        userService.create(request);

        ArgumentCaptor<SysUserDO> captor = ArgumentCaptor.forClass(SysUserDO.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("/uploads/avatar.png", captor.getValue().getAvatar());
    }

    @Test
    void assignRolesEvictsUserPermissions() {
        SysUserDO user = new SysUserDO();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(dataScopeHelper.isAdmin()).thenReturn(true);

        userService.assignRoles(7L, List.of(2L, 3L));

        verify(userRoleMapper).deleteByUserId(7L);
        verify(userRoleMapper).insert(eq(7L), eq(2L));
        verify(userRoleMapper).insert(eq(7L), eq(3L));
        // 角色变更同时失效角色+权限缓存（否则旧角色编码残留）
        verify(tokenService).evictUserRolesAndPermissionsAfterCommit(7L);
    }

    @Test
    void assignRolesClearAlsoEvicts() {
        SysUserDO user = new SysUserDO();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(dataScopeHelper.isAdmin()).thenReturn(true);

        // 清空角色必须失效角色+权限缓存，否则用户仍持旧角色/旧权限直到 TTL
        userService.assignRoles(7L, List.of());

        verify(userRoleMapper).deleteByUserId(7L);
        verify(tokenService).evictUserRolesAndPermissionsAfterCommit(7L);
    }

    @Test
    void deleteUserEvictsPermissions() {
        // 删除用户后残留权限缓存（TTL 30 分钟）无意义且占位，一并失效
        SysUserDO user = new SysUserDO();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(dataScopeHelper.isAdmin()).thenReturn(true);

        userService.delete(7L);

        verify(userMapper).deleteById(7L);
        verify(userRoleMapper).deleteByUserId(7L);
        verify(userPostMapper).deleteByUserId(7L);
        verify(tokenService).evictUserPermissionsAfterCommit(7L);
    }

    @Test
    void updateStatusEvictsPermissions() {
        // 禁用/重新启用均清除权限缓存——重新启用后若缓存为禁用前旧快照会残留旧权限
        SysUserDO user = new SysUserDO();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(dataScopeHelper.isAdmin()).thenReturn(true);

        userService.updateStatus(7L, 0);

        verify(userMapper).updateById(user);
        verify(tokenService).evictUserPermissionsAfterCommit(7L);
    }

    // ---------- 写路径数据范围校验 ----------

    /** 非 admin 编辑数据范围外的用户：按 id 直查绕过 page 过滤的路径必须被归属校验拦下。 */
    @Test
    void updateRejectsOtherUserForNonAdmin() {
        SysUserDO other = new SysUserDO();
        other.setId(8L);
        when(userMapper.selectById(8L)).thenReturn(other);
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));

        UserSaveRequest request = new UserSaveRequest();
        request.setId(8L);
        request.setUsername("alice");

        assertThatThrownBy(() -> userService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORBIDDEN.getMessage());
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    /** 非 admin 删除数据范围外的用户被拒，且不触碰删除链路。 */
    @Test
    void deleteRejectsOtherUserForNonAdmin() {
        SysUserDO other = new SysUserDO();
        other.setId(8L);
        when(userMapper.selectById(8L)).thenReturn(other);
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));

        assertThatThrownBy(() -> userService.delete(8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORBIDDEN.getMessage());
        verify(userMapper, never()).deleteById(8L);
    }

    /** 非 admin 给数据范围外的用户提权（分配角色）被拒——防越权提权。 */
    @Test
    void assignRolesRejectsOtherUserForNonAdmin() {
        SysUserDO other = new SysUserDO();
        other.setId(8L);
        when(userMapper.selectById(8L)).thenReturn(other);
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L));

        assertThatThrownBy(() -> userService.assignRoles(8L, List.of(2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.FORBIDDEN.getMessage());
        verify(userRoleMapper, never()).insert(eq(8L), eq(2L));
    }

    /** 非 admin 编辑自己数据范围内的用户正常放行。 */
    @Test
    void updateAllowsOwnUserForNonAdmin() {
        SysUserDO own = new SysUserDO();
        own.setId(8L);
        when(userMapper.selectById(8L)).thenReturn(own);
        when(dataScopeHelper.isAdmin()).thenReturn(false);
        when(dataScopeHelper.allowedUserIds()).thenReturn(List.of(7L, 8L));

        UserSaveRequest request = new UserSaveRequest();
        request.setId(8L);
        request.setUsername("alice");

        userService.update(request);

        verify(userMapper).updateById(own);
    }
}
