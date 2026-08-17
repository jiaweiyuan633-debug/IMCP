package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.module.system.entity.SysDeptDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysDeptMapper;
import com.example.admin.module.system.mapper.SysRoleDeptMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.common.TenantContext;
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
    private final SysUserMapper userMapper;

    public boolean isAdmin() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        return loginUser.getRoles() != null && loginUser.getRoles().contains("admin");
    }

    public List<Long> allowedUserIds() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (isAdmin()) {
            return null;
        }
        List<Integer> scopes = roleMapper.selectDataScopesByUserId(loginUser.getUserId());
        if (scopes.contains(SCOPE_ALL)) {
            return null;
        }
        if (scopes.contains(SCOPE_CUSTOM)) {
            List<Long> deptIds = customDeptIds(loginUser.getUserId());
            return deptIds.isEmpty() ? List.of(-1L) : userIdsByDepts(deptIds);
        }
        if (scopes.contains(SCOPE_DEPT_AND_CHILD)) {
            List<Long> deptIds = deptAndChildIds(loginUser.getDeptId());
            return deptIds.isEmpty() ? List.of(-1L) : userIdsByDepts(deptIds);
        }
        if (scopes.contains(SCOPE_DEPT)) {
            return userIdsByDepts(List.of(loginUser.getDeptId()));
        }
        return List.of(loginUser.getUserId());
    }

    public List<String> allowedUsernames() {
        if (isAdmin()) {
            return null;
        }
        List<Long> userIds = allowedUserIds();
        if (userIds == null) {
            return null;
        }
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUserDO>()
                        .in(SysUserDO::getId, userIds)
                        .eq(SysUserDO::getTenantId, TenantContext.getTenantId()))
                .stream()
                .map(SysUserDO::getUsername)
                .toList();
    }

    private List<Long> userIdsByDepts(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty() || deptIds.contains(null)) {
            return List.of(-1L);
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUserDO>()
                        .in(SysUserDO::getDeptId, deptIds)
                        .eq(SysUserDO::getTenantId, TenantContext.getTenantId()))
                .stream()
                .map(SysUserDO::getId)
                .toList();
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
        // 批次4（R4-1.50）：单条 ancestors LIKE 查询替代全表载入内存扫描——原实现
        // selectList(null) 每请求拉全量部门表再逐行 contains 匹配祖先串，部门表大时
        // 每次数据权限计算都是一次全表扫描 + 内存遍历
        List<SysDeptDO> children = deptMapper.selectList(new LambdaQueryWrapper<SysDeptDO>()
                .like(SysDeptDO::getAncestors, "," + deptId + ","));
        List<Long> result = new ArrayList<>(children.size() + 1);
        result.add(deptId);
        for (SysDeptDO dept : children) {
            result.add(dept.getId());
        }
        return result;
    }
}

