package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.UserExcelDTO;
import com.example.admin.module.system.dto.UserQuery;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.entity.SysRole;
import com.example.admin.module.system.entity.SysDept;
import com.example.admin.module.system.entity.SysPost;
import com.example.admin.module.system.entity.SysUser;
import com.example.admin.module.system.mapper.SysDeptMapper;
import com.example.admin.module.system.mapper.SysPostMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.mapper.SysUserPostMapper;
import com.example.admin.security.TokenService;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.vo.UserVo;
import com.alibaba.excel.EasyExcel;
import com.example.admin.module.system.entity.SysConfig;
import jakarta.servlet.http.HttpServletResponse;
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
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SystemUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final DataScopeHelper dataScopeHelper;
    private final SysConfigMapper configMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public PageResult<UserVo> page(UserQuery query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), SysUser::getNickname, query.getNickname())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getId);
        dataScopeHelper.apply(wrapper);
        wrapper.eq(SysUser::getTenantId, TenantContext.getTenantId());
        IPage<SysUser> result = userMapper.selectPage(page, wrapper);
        List<SysUser> users = result.getRecords();
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        if (userIds.isEmpty()) {
            return PageResult.of(result, List.of());
        }
        Map<Long, SysDept> deptMap = users.stream()
                .map(SysUser::getDeptId)
                .filter(id -> id != null)
                .distinct()
                .toList()
                .isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(users.stream().map(SysUser::getDeptId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(SysDept::getId, Function.identity()));
        Map<Long, List<Long>> roleIdsByUser = groupByUser(userRoleMapper.selectByUserIds(userIds), "role_id", "roleId");
        Map<Long, List<Long>> postIdsByUser = groupByUser(userPostMapper.selectByUserIds(userIds), "post_id", "postId");
        Map<Long, SysRole> roleMap = loadMap(roleIdsByUser, roleMapper::selectBatchIds);
        Map<Long, SysPost> postMap = loadMap(postIdsByUser, postMapper::selectBatchIds);
        List<UserVo> records = users.stream()
                .map(user -> toVo(
                        user,
                        deptMap,
                        roleIdsByUser.getOrDefault(user.getId(), Collections.emptyList()),
                        postIdsByUser.getOrDefault(user.getId(), Collections.emptyList()),
                        roleMap,
                        postMap))
                .toList();
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
        user.setTenantId(TenantContext.getTenantId());
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setDeptId(request.getDeptId());
        userMapper.insert(user);
        if (request.getRoleIds() != null) {
            assignRoles(user.getId(), request.getRoleIds());
        }
        if (request.getPostIds() != null) {
            assignPosts(user.getId(), request.getPostIds());
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
        user.setDeptId(request.getDeptId());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        userMapper.updateById(user);
        if (request.getRoleIds() != null) {
            assignRoles(user.getId(), request.getRoleIds());
        }
        if (request.getPostIds() != null) {
            assignPosts(user.getId(), request.getPostIds());
        }
    }

    @Transactional
    public void delete(Long id) {
        userMapper.deleteById(id);
        userRoleMapper.deleteByUserId(id);
        userPostMapper.deleteByUserId(id);
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
        tokenService.evictAllPermissions();
    }

    public void assignPosts(Long userId, List<Long> postIds) {
        userPostMapper.deleteByUserId(userId);
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : postIds) {
            userPostMapper.insert(userId, postId);
        }
    }

    private UserVo toVo(
            SysUser user,
            Map<Long, SysDept> deptMap,
            List<Long> roleIds,
            List<Long> postIds,
            Map<Long, SysRole> roleMap,
            Map<Long, SysPost> postMap) {
        List<String> roleNames = roleIds.stream()
                .map(id -> roleMap.get(id))
                .filter(role -> role != null)
                .map(SysRole::getName)
                .toList();
        SysDept dept = user.getDeptId() == null ? null : deptMap.get(user.getDeptId());
        List<String> postNames = postIds.stream()
                .map(id -> postMap.get(id))
                .filter(post -> post != null)
                .map(SysPost::getPostName)
                .toList();
        return UserVo.builder()
                .id(user.getId())
                .deptId(user.getDeptId())
                .deptName(dept == null ? null : dept.getDeptName())
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
                .postIds(postIds)
                .postNames(postNames)
                .build();
    }

    private Map<Long, List<Long>> groupByUser(List<Map<String, Object>> rows, String snakeKey, String camelKey) {
        return rows.stream().collect(Collectors.groupingBy(
                row -> longValue(row, "user_id", "userId"),
                Collectors.mapping(row -> longValue(row, snakeKey, camelKey), Collectors.toList())));
    }

    private <T> Map<Long, T> loadMap(
            Map<Long, List<Long>> idsByUser,
            java.util.function.Function<java.util.Collection<Long>, java.util.List<T>> loader) {
        List<Long> ids = idsByUser.values().stream().flatMap(List::stream).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(ids).stream()
                .collect(Collectors.toMap(this::entityId, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    private Long entityId(Object entity) {
        if (entity instanceof SysRole role) {
            return role.getId();
        }
        if (entity instanceof SysPost post) {
            return post.getId();
        }
        throw new IllegalArgumentException("Unsupported entity");
    }

    private Long longValue(Map<String, Object> row, String snakeKey, String camelKey) {
        Object value = row.containsKey(snakeKey) ? row.get(snakeKey) : row.get(camelKey);
        return ((Number) value).longValue();
    }

    public void exportUsers(HttpServletResponse response) throws IOException {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getId);
        dataScopeHelper.apply(wrapper);
        List<UserExcelDTO> rows = userMapper.selectList(wrapper).stream().map(user -> {
            UserExcelDTO dto = new UserExcelDTO();
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname());
            dto.setEmail(user.getEmail());
            dto.setPhone(user.getPhone());
            dto.setStatus(user.getStatus());
            return dto;
        }).toList();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户数据", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), UserExcelDTO.class).sheet("用户").doWrite(rows);
    }

    public int importUsers(MultipartFile file) throws IOException {
        List<UserExcelDTO> rows = EasyExcel.read(file.getInputStream())
                .head(UserExcelDTO.class)
                .sheet()
                .doReadSync();
        String defaultPassword = defaultPassword();
        int count = 0;
        for (UserExcelDTO row : rows) {
            if (row.getUsername() == null || row.getUsername().isBlank()) {
                continue;
            }
            boolean exists = userMapper.exists(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, row.getUsername().trim()));
            if (exists) {
                throw new BusinessException(1006, "导入失败，用户名已存在：" + row.getUsername());
            }
            SysUser user = new SysUser();
            user.setTenantId(TenantContext.getTenantId());
            user.setUsername(row.getUsername().trim());
            user.setPassword(passwordEncoder.encode(defaultPassword));
            user.setNickname(row.getNickname());
            user.setEmail(row.getEmail());
            user.setPhone(row.getPhone());
            user.setStatus(row.getStatus() == null ? 1 : row.getStatus());
            userMapper.insert(user);
            count++;
        }
        return count;
    }

    private String defaultPassword() {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "sys.user.initPassword"));
        return config == null ? "admin123" : config.getConfigValue();
    }
}

