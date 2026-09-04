package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.UniqueKeyRelease;
import cn.admin.scaffold.common.annotation.FieldAudit;
import cn.admin.scaffold.module.system.dto.RoleQuery;
import cn.admin.scaffold.module.system.dto.RoleSaveRequest;
import cn.admin.scaffold.module.system.entity.SysRoleDO;
import cn.admin.scaffold.module.system.mapper.SysRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleMenuMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleDeptMapper;
import cn.admin.scaffold.module.system.mapper.SysUserRoleMapper;
import cn.admin.scaffold.security.TokenService;
import cn.admin.scaffold.module.system.vo.RoleOptionVo;
import cn.admin.scaffold.module.system.vo.RoleVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemRoleService {

    private static final int ENABLED = 1;
    private static final int DEFAULT_SORT = 0;
    private static final int DEFAULT_DATA_SCOPE = 1;

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final TokenService tokenService;

    public PageResult<RoleVo> page(RoleQuery query) {
        Page<SysRoleDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysRoleDO> wrapper = new LambdaQueryWrapper<SysRoleDO>()
                .like(StringUtils.hasText(query.getCode()), SysRoleDO::getCode, query.getCode())
                .like(StringUtils.hasText(query.getName()), SysRoleDO::getName, query.getName())
                .eq(query.getStatus() != null, SysRoleDO::getStatus, query.getStatus())
                .orderByAsc(SysRoleDO::getSort)
                .orderByAsc(SysRoleDO::getId);
        IPage<SysRoleDO> result = roleMapper.selectPage(page, wrapper);
        List<RoleVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public List<RoleOptionVo> options() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRoleDO>()
                        .eq(SysRoleDO::getStatus, ENABLED)
                        .orderByAsc(SysRoleDO::getSort))
                .stream()
                .map(role -> RoleOptionVo.builder()
                        .id(role.getId())
                        .code(role.getCode())
                        .name(role.getName())
                        .build())
                .toList();
    }

    @Transactional
    public Long create(RoleSaveRequest request) {
        boolean exists = roleMapper.exists(
                new LambdaQueryWrapper<SysRoleDO>().eq(SysRoleDO::getCode, request.getCode().trim()));
        if (exists) {
            throw new BusinessException(ResultCode.ROLE_CODE_EXISTS);
        }
        SysRoleDO role = toEntity(request);
        roleMapper.insert(role);
        if (request.getMenuIds() != null) {
            assignMenus(role.getId(), request.getMenuIds());
        }
        if (request.getDeptIds() != null) {
            assignDepts(role.getId(), request.getDeptIds());
        }
        return role.getId();
    }

    @Transactional
    @FieldAudit(entity = SysRoleDO.class, action = "UPDATE", module = "角色管理")
    public void update(RoleSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "角色 ID 不能为空");
        }
        SysRoleDO role = roleMapper.selectById(request.getId());
        if (role == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        SysRoleDO sameCode = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRoleDO>().eq(SysRoleDO::getCode, request.getCode().trim()));
        if (sameCode != null && !sameCode.getId().equals(request.getId())) {
            throw new BusinessException(ResultCode.ROLE_CODE_EXISTS);
        }
        role.setCode(request.getCode().trim());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() == null ? role.getStatus() : request.getStatus());
        role.setDataScope(request.getDataScope() == null ? role.getDataScope() : request.getDataScope());
        role.setSort(request.getSort() == null ? Integer.valueOf(DEFAULT_SORT) : request.getSort());
        roleMapper.updateById(role);
        if (request.getMenuIds() != null) {
            assignMenus(role.getId(), request.getMenuIds());
        }
        if (request.getDeptIds() != null) {
            assignDepts(role.getId(), request.getDeptIds());
        }
    }

    @Transactional
    public void delete(Long id) {
        // R4-1.31：删除角色后拥有者仍持旧权限（缓存 TTL 30 分钟）是缺陷——删除前收集绑定用户，
        // 提交后失效其角色+权限缓存，使撤销的角色/权限立即生效（批次2：角色缓存一并失效）
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleIds(List.of(id));
        // 批次4（R4-1.50）：逻辑删除 + (tenant_id, code) 唯一键冲突——删除前释放 code 唯一键
        SysRoleDO role = roleMapper.selectById(id);
        if (role != null) {
            role.setCode(UniqueKeyRelease.releaseCode(role.getCode()));
            roleMapper.updateById(role);
        }
        roleMapper.deleteById(id);
        roleMenuMapper.deleteByRoleId(id);
        roleDeptMapper.deleteByRoleId(id);
        tokenService.evictRolesAndPermissionsByUserIdsAfterCommit(userIds);
    }

    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        List<Long> userIds = userRoleMapper.selectUserIdsByRoleIds(List.of(roleId));
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds != null && !menuIds.isEmpty()) {
            roleMenuMapper.insertBatch(roleId, menuIds);
        }
        // 清空与重设都需失效权限缓存（清空后用户仍持旧权限是缺陷）；只失效绑定该角色的用户，避免 KEYS 全扫与缓存雪崩。
        // R4-1.12：提交前删除存在竞态——并发请求在 evict 后、commit 前读库（旧权限）会重新缓存，
        // 撤销的权限最长残留 30 分钟。改为事务提交后失效。
        tokenService.evictPermissionsByUserIdsAfterCommit(userIds);
    }

    public void assignDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.deleteByRoleId(roleId);
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        roleDeptMapper.insertBatch(roleId, deptIds);
    }

    private SysRoleDO toEntity(RoleSaveRequest request) {
        SysRoleDO role = new SysRoleDO();
        role.setCode(request.getCode().trim());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        role.setDataScope(request.getDataScope() == null ? Integer.valueOf(DEFAULT_DATA_SCOPE) : request.getDataScope());
        role.setSort(request.getSort() == null ? Integer.valueOf(DEFAULT_SORT) : request.getSort());
        return role;
    }

    private RoleVo toVo(SysRoleDO role) {
        List<Long> menuIds = roleMenuMapper.selectMenuIdsByRoleId(role.getId());
        return RoleVo.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .dataScope(role.getDataScope())
                .sort(role.getSort())
                .createdAt(role.getCreatedAt())
                .menuIds(menuIds == null ? Collections.emptyList() : menuIds)
                .deptIds(roleDeptMapper.selectDeptIdsByRoleId(role.getId()))
                .build();
    }
}

