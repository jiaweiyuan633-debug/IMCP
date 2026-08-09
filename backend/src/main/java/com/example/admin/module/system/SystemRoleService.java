package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.RoleQuery;
import com.example.admin.module.system.dto.RoleSaveRequest;
import com.example.admin.module.system.entity.SysRole;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysRoleMenuMapper;
import com.example.admin.module.system.mapper.SysRoleDeptMapper;
import com.example.admin.security.TokenService;
import com.example.admin.module.system.vo.RoleOptionVo;
import com.example.admin.module.system.vo.RoleVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final TokenService tokenService;

    public PageResult<RoleVo> page(RoleQuery query) {
        Page<SysRole> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(query.getCode()), SysRole::getCode, query.getCode())
                .like(StringUtils.hasText(query.getName()), SysRole::getName, query.getName())
                .eq(query.getStatus() != null, SysRole::getStatus, query.getStatus())
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getId);
        IPage<SysRole> result = roleMapper.selectPage(page, wrapper);
        List<RoleVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public List<RoleOptionVo> options() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getStatus, 1)
                        .orderByAsc(SysRole::getSort))
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
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, request.getCode().trim()));
        if (exists) {
            throw new BusinessException(1007, "角色编码已存在");
        }
        SysRole role = toEntity(request);
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
    public void update(RoleSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "角色 ID 不能为空");
        }
        SysRole role = roleMapper.selectById(request.getId());
        if (role == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        SysRole sameCode = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, request.getCode().trim()));
        if (sameCode != null && !sameCode.getId().equals(request.getId())) {
            throw new BusinessException(1007, "角色编码已存在");
        }
        role.setCode(request.getCode().trim());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus());
        role.setDataScope(request.getDataScope());
        role.setSort(request.getSort() == null ? 0 : request.getSort());
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
        roleMapper.deleteById(id);
        roleMenuMapper.deleteByRoleId(id);
        roleDeptMapper.deleteByRoleId(id);
    }

    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        roleMenuMapper.insertBatch(roleId, menuIds);
        tokenService.evictAllPermissions();
    }

    public void assignDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.deleteByRoleId(roleId);
        if (deptIds == null || deptIds.isEmpty()) {
            return;
        }
        roleDeptMapper.insertBatch(roleId, deptIds);
    }

    private SysRole toEntity(RoleSaveRequest request) {
        SysRole role = new SysRole();
        role.setCode(request.getCode().trim());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        role.setDataScope(request.getDataScope() == null ? 1 : request.getDataScope());
        role.setSort(request.getSort() == null ? 0 : request.getSort());
        return role;
    }

    private RoleVo toVo(SysRole role) {
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

