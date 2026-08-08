package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.UserQuery;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.entity.SysRole;
import com.example.admin.module.system.entity.SysUser;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVo> page(UserQuery query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), SysUser::getNickname, query.getNickname())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getId);
        IPage<SysUser> result = userMapper.selectPage(page, wrapper);
        List<UserVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    @Transactional
    public Long create(UserSaveRequest request) {
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername().trim()));
        if (exists) {
            throw new BusinessException(1006, "用户名已存在");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "密码不能为空");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        userMapper.insert(user);
        if (request.getRoleIds() != null) {
            assignRoles(user.getId(), request.getRoleIds());
        }
        return user.getId();
    }

    @Transactional
    public void update(UserSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户 ID 不能为空");
        }
        SysUser user = userMapper.selectById(request.getId());
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        SysUser sameName = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername().trim()));
        if (sameName != null && !sameName.getId().equals(request.getId())) {
            throw new BusinessException(1006, "用户名已存在");
        }
        user.setUsername(request.getUsername().trim());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        userMapper.updateById(user);
        if (request.getRoleIds() != null) {
            assignRoles(user.getId(), request.getRoleIds());
        }
    }

    @Transactional
    public void delete(Long id) {
        userMapper.deleteById(id);
        userRoleMapper.deleteByUserId(id);
    }

    public void updateStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            userRoleMapper.insert(userId, roleId);
        }
    }

    private UserVo toVo(SysUser user) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
        List<SysRole> roles = roleIds.isEmpty()
                ? Collections.emptyList()
                : roleMapper.selectBatchIds(roleIds);
        Map<Long, SysRole> roleMap = roles.stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        List<String> roleNames = roleIds.stream()
                .map(id -> roleMap.get(id))
                .filter(role -> role != null)
                .map(SysRole::getName)
                .toList();
        return UserVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createdAt(user.getCreatedAt())
                .roleIds(roleIds)
                .roleNames(roleNames)
                .build();
    }
}

