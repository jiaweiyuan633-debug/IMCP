package com.example.admin.module.system;

import com.example.admin.common.TenantContext;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.entity.SysTenant;
import com.example.admin.module.system.entity.SysUser;
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
            SysUser user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

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
}
