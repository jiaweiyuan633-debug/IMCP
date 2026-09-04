package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.module.system.entity.SysDeptDO;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.mapper.SysDeptMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleDeptMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysUserRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.security.LoginUser;
import cn.admin.scaffold.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
            // 用户可能未分配部门（deptId 为 null）：List.of(null) 直接 NPE，视为无可见数据
            Long deptId = loginUser.getDeptId();
            return deptId == null ? List.of(-1L) : userIdsByDepts(List.of(deptId));
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
        // 不能用 deptIds.contains(null)：List.of 返回的不可变列表对 contains(null) 直接
        // Objects.requireNonNull 抛 NPE，需改用 anyMatch 判空（兼容可变/不可变列表）
        if (deptIds == null || deptIds.isEmpty() || deptIds.stream().anyMatch(Objects::isNull)) {
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
        // 直接子部门的 ancestors 以 {deptId} 结尾（如研发部祖先 '0,1'），单用 `% ,{id},%`
        // 匹配不到结尾段，需并上 likeLeft 后缀匹配，才能纳入「本部门及以下」的直接子部门。
        List<SysDeptDO> children = deptMapper.selectList(new LambdaQueryWrapper<SysDeptDO>()
                .and(w -> w.like(SysDeptDO::getAncestors, "," + deptId + ",")
                        .or()
                        .likeLeft(SysDeptDO::getAncestors, "," + deptId)));
        List<Long> result = new ArrayList<>(children.size() + 1);
        result.add(deptId);
        for (SysDeptDO dept : children) {
            result.add(dept.getId());
        }
        return result;
    }
}

