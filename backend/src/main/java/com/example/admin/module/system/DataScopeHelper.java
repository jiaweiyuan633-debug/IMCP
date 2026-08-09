package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.system.entity.SysDept;
import com.example.admin.module.system.entity.SysUser;
import com.example.admin.module.system.mapper.SysDeptMapper;
import com.example.admin.module.system.mapper.SysRoleDeptMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataScopeHelper {

    public static final int SCOPE_ALL = 1;
    public static final int SCOPE_CUSTOM = 2;
    public static final int SCOPE_DEPT = 3;
    public static final int SCOPE_DEPT_AND_CHILD = 4;
    public static final int SCOPE_SELF = 5;

    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    public LambdaQueryWrapper<SysUser> apply(LambdaQueryWrapper<SysUser> wrapper) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser.getRoles() != null && loginUser.getRoles().contains("admin")) {
            return wrapper;
        }
        List<Integer> scopes = roleMapper.selectDataScopesByUserId(loginUser.getUserId());
        if (scopes.contains(SCOPE_ALL)) {
            return wrapper;
        }
        if (scopes.contains(SCOPE_CUSTOM)) {
            List<Long> deptIds = customDeptIds(loginUser.getUserId());
            if (!deptIds.isEmpty()) {
                wrapper.in(SysUser::getDeptId, deptIds);
            } else {
                wrapper.eq(SysUser::getId, -1L);
            }
            return wrapper;
        }
        if (scopes.contains(SCOPE_DEPT_AND_CHILD)) {
            List<Long> deptIds = deptAndChildIds(loginUser.getDeptId());
            if (!deptIds.isEmpty()) {
                wrapper.in(SysUser::getDeptId, deptIds);
            } else {
                wrapper.eq(SysUser::getId, -1L);
            }
            return wrapper;
        }
        if (scopes.contains(SCOPE_DEPT)) {
            wrapper.eq(SysUser::getDeptId, loginUser.getDeptId());
            return wrapper;
        }
        wrapper.eq(SysUser::getId, loginUser.getUserId());
        return wrapper;
    }

    private List<Long> customDeptIds(Long userId) {
        Set<Long> deptIds = new HashSet<>();
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        for (Long roleId : roleIds) {
            deptIds.addAll(roleDeptMapper.selectDeptIdsByRoleId(roleId));
        }
        return new ArrayList<>(deptIds);
    }

    private List<Long> deptAndChildIds(Long deptId) {
        if (deptId == null) {
            return List.of();
        }
        List<SysDept> depts = deptMapper.selectList(null);
        List<Long> result = new ArrayList<>();
        result.add(deptId);
        for (SysDept dept : depts) {
            if (dept.getAncestors() != null && dept.getAncestors().contains("," + deptId + ",")) {
                result.add(dept.getId());
            }
        }
        return result;
    }
}

