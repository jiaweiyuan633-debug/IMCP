package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.PasswordPolicy;
import com.example.admin.common.ResultCode;
import com.example.admin.common.UniqueKeyRelease;
import com.example.admin.module.system.dto.UserExcelDTO;
import com.example.admin.module.system.dto.UserQuery;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.entity.SysRoleDO;
import com.example.admin.module.system.entity.SysDeptDO;
import com.example.admin.module.system.entity.SysPostDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysDeptMapper;
import com.example.admin.module.system.mapper.SysPostMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysConfigMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.mapper.SysUserPostMapper;
import com.example.admin.module.system.mapper.SysTenantMapper;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.security.TokenService;
import com.example.admin.common.TenantContext;
import com.example.admin.common.annotation.DataScope;
import com.example.admin.common.annotation.FieldAudit;
import com.example.admin.module.system.vo.UserVo;
import com.alibaba.excel.EasyExcel;
import com.example.admin.module.system.entity.SysConfigDO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SystemUserService {

    private static final String DEFAULT_INIT_PASSWORD = "admin123";
    private static final int ENABLED = 1;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final DataScopeHelper dataScopeHelper;
    private final SysConfigMapper configMapper;
    private final SysTenantMapper tenantMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @DataScope(tables = {"sys_user"})
    public PageResult<UserVo> page(UserQuery query) {
        Page<SysUserDO> page = new Page<>(query.getPageNum(), query.getPageSize(), false);
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<SysUserDO>()
                .like(StringUtils.hasText(query.getUsername()), SysUserDO::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), SysUserDO::getNickname, query.getNickname())
                .eq(query.getStatus() != null, SysUserDO::getStatus, query.getStatus())
                .orderByDesc(SysUserDO::getId);
        wrapper.eq(SysUserDO::getTenantId, TenantContext.getTenantId());
        IPage<SysUserDO> result = userMapper.selectPage(page, wrapper);
        page.setTotal(userMapper.selectCount(wrapper));
        List<SysUserDO> users = result.getRecords();
        List<Long> userIds = users.stream().map(SysUserDO::getId).toList();
        if (userIds.isEmpty()) {
            return PageResult.of(result, List.of());
        }
        Map<Long, SysDeptDO> deptMap = users.stream()
                .map(SysUserDO::getDeptId)
                .filter(id -> id != null)
                .distinct()
                .toList()
                .isEmpty() ? Map.of()
                : deptMapper.selectBatchIds(users.stream().map(SysUserDO::getDeptId)
                        .filter(id -> id != null)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(SysDeptDO::getId, Function.identity()));
        Map<Long, List<Long>> roleIdsByUser = groupByUser(userRoleMapper.selectByUserIds(userIds), "role_id", "roleId");
        Map<Long, List<Long>> postIdsByUser = groupByUser(userPostMapper.selectByUserIds(userIds), "post_id", "postId");
        Map<Long, SysRoleDO> roleMap = loadMap(roleIdsByUser, roleMapper::selectBatchIds);
        Map<Long, SysPostDO> postMap = loadMap(postIdsByUser, postMapper::selectBatchIds);
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
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getUsername, request.getUsername().trim()));
        if (exists) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "密码不能为空");
        }
        // R4-1.40：Service 层复杂度兜底（Controller @Valid 之外防内部调用绕过），与 PasswordPolicy 共用规则
        if (!PasswordPolicy.matches(request.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), PasswordPolicy.MESSAGE);
        }
        checkTenantUserLimit();
        SysUserDO user = new SysUserDO();
        user.setTenantId(TenantContext.getTenantId());
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        user.setDeptId(request.getDeptId());
        userMapper.insert(user);
        // 新建用户归创建者管理，不走公开入口的归属校验（新用户 id 不在创建者可见集合内）
        if (request.getRoleIds() != null) {
            assignRolesInternal(user.getId(), request.getRoleIds());
        }
        if (request.getPostIds() != null) {
            assignPostsInternal(user.getId(), request.getPostIds());
        }
        return user.getId();
    }

    @Transactional
    @FieldAudit(entity = SysUserDO.class, action = "UPDATE", module = "用户管理")
    public void update(UserSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户 ID 不能为空");
        }
        // R4-1.39：page 按数据范围过滤但按 id 直查可绕过，编辑前先做归属校验
        SysUserDO user = loadUserOrThrow(request.getId());
        checkUserDataScope(user);
        SysUserDO sameName = userMapper.selectOne(
                new LambdaQueryWrapper<SysUserDO>().eq(SysUserDO::getUsername, request.getUsername().trim()));
        if (sameName != null && !sameName.getId().equals(request.getId())) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        user.setUsername(request.getUsername().trim());
        if (StringUtils.hasText(request.getPassword())) {
            // R4-1.40：编辑改密同样走 PasswordPolicy 复杂度校验
            if (!PasswordPolicy.matches(request.getPassword())) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), PasswordPolicy.MESSAGE);
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDeptId(request.getDeptId());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        userMapper.updateById(user);
        if (request.getRoleIds() != null) {
            assignRolesInternal(user.getId(), request.getRoleIds());
        }
        if (request.getPostIds() != null) {
            assignPostsInternal(user.getId(), request.getPostIds());
        }
    }

    @Transactional
    public void delete(Long id) {
        SysUserDO user = loadUserOrThrow(id);
        checkUserDataScope(user);
        // 批次4（R4-1.50）：逻辑删除 + (tenant_id, username) 唯一键冲突——删除前释放 username
        // 唯一键（改为 原username#del#时间戳），否则删除后同名账号永远无法重建
        user.setUsername(UniqueKeyRelease.releaseCode(user.getUsername()));
        userMapper.updateById(user);
        userMapper.deleteById(id);
        userRoleMapper.deleteByUserId(id);
        userPostMapper.deleteByUserId(id);
        // R4-1.31：删除用户后残留权限缓存（TTL 30 分钟）无意义且占位，一并失效
        tokenService.evictUserPermissionsAfterCommit(id);
    }

    public void updateStatus(Long id, Integer status) {
        // R4-1.39：page 受控但按 id 直查可绕过，禁用/启用前先做归属校验
        SysUserDO user = loadUserOrThrow(id);
        checkUserDataScope(user);
        user.setStatus(status);
        userMapper.updateById(user);
        // R4-1.31：禁用/重新启用均清除权限缓存——重新启用后若缓存为禁用前旧快照（期间角色
        // 权限已变但用户非绑定角色路径未触达失效），会残留旧权限；清除后下次请求按库重新缓存
        tokenService.evictUserPermissionsAfterCommit(id);
    }

    /** 公开入口（Controller 调用）：R4-1.39 分配角色前先校验目标用户归属，防按 id 越权提权。 */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        checkUserDataScope(loadUserOrThrow(userId));
        assignRolesInternal(userId, roleIds);
    }

    /** 公开入口（Controller 调用）：R4-1.39 分配岗位前先校验目标用户归属。 */
    public void assignPosts(Long userId, List<Long> postIds) {
        checkUserDataScope(loadUserOrThrow(userId));
        assignPostsInternal(userId, postIds);
    }

    /** 无归属校验的分配实现：仅供 create/update 内部复用（目标用户已在调用方完成存在性与归属校验）。 */
    private void assignRolesInternal(Long userId, List<Long> roleIds) {
        userRoleMapper.deleteByUserId(userId);
        // 清空与重设都需失效角色+权限缓存（清空后用户仍持旧角色/旧权限是缺陷）；只失效该用户，避免全局 KEYS 全扫与缓存雪崩。
        // R4-1.12：提交前删除存在竞态——并发请求在 evict 后、commit 前读库（旧角色）会重新缓存
        // 旧角色/权限，撤销的最长残留 30 分钟。改为事务提交后失效（批次2：角色缓存一并失效）。
        tokenService.evictUserRolesAndPermissionsAfterCommit(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            userRoleMapper.insert(userId, roleId);
        }
    }

    private void assignPostsInternal(Long userId, List<Long> postIds) {
        userPostMapper.deleteByUserId(userId);
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : postIds) {
            userPostMapper.insert(userId, postId);
        }
    }

    /**
     * 单条归属校验（R4-1.39）：sys_user page/export 已按数据范围过滤，但 update/delete/updateStatus/
     * assignRoles/assignPosts 按 id 直查后直接操作，非 admin 可猜测/遍历 id 越权改密/删号/提权。
     * 与 FormInstanceService.checkDataScope 同一语义：admin 短路，allowedUserIds 为 null 放行，
     * 否则目标用户 id 必须命中当前用户可见集合，越权抛 FORBIDDEN。
     */
    private void checkUserDataScope(SysUserDO user) {
        if (dataScopeHelper.isAdmin()) {
            return;
        }
        List<Long> allowedUserIds = dataScopeHelper.allowedUserIds();
        if (allowedUserIds == null) {
            return;
        }
        if (user.getId() == null || !allowedUserIds.contains(user.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private SysUserDO loadUserOrThrow(Long id) {
        SysUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return user;
    }

    private UserVo toVo(
            SysUserDO user,
            Map<Long, SysDeptDO> deptMap,
            List<Long> roleIds,
            List<Long> postIds,
            Map<Long, SysRoleDO> roleMap,
            Map<Long, SysPostDO> postMap) {
        List<String> roleNames = roleIds.stream()
                .map(id -> roleMap.get(id))
                .filter(role -> role != null)
                .map(SysRoleDO::getName)
                .toList();
        SysDeptDO dept = user.getDeptId() == null ? null : deptMap.get(user.getDeptId());
        List<String> postNames = postIds.stream()
                .map(id -> postMap.get(id))
                .filter(post -> post != null)
                .map(SysPostDO::getPostName)
                .toList();
        boolean mask = !dataScopeHelper.isAdmin();
        return UserVo.builder()
                .id(user.getId())
                .deptId(user.getDeptId())
                .deptName(dept == null ? null : dept.getDeptName())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .email(mask ? maskEmail(user.getEmail()) : user.getEmail())
                .phone(mask ? maskPhone(user.getPhone()) : user.getPhone())
                .status(user.getStatus())
                .lastLoginTime(user.getLastLoginTime())
                .createdAt(user.getCreatedAt())
                .roleIds(roleIds)
                .roleNames(roleNames)
                .postIds(postIds)
                .postNames(postNames)
                .build();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 2);
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
        if (entity instanceof SysRoleDO role) {
            return role.getId();
        }
        if (entity instanceof SysPostDO post) {
            return post.getId();
        }
        throw new IllegalArgumentException("Unsupported entity");
    }

    private Long longValue(Map<String, Object> row, String snakeKey, String camelKey) {
        Object value = row.containsKey(snakeKey) ? row.get(snakeKey) : row.get(camelKey);
        return ((Number) value).longValue();
    }

    @DataScope(tables = {"sys_user"})
    public void exportUsers(HttpServletResponse response) throws IOException {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<SysUserDO>()
                .orderByDesc(SysUserDO::getId);
        boolean mask = !dataScopeHelper.isAdmin();
        List<UserExcelDTO> rows = userMapper.selectList(wrapper).stream().map(user -> {
            UserExcelDTO dto = new UserExcelDTO();
            dto.setUsername(user.getUsername());
            dto.setNickname(user.getNickname());
            dto.setEmail(mask ? maskEmail(user.getEmail()) : user.getEmail());
            dto.setPhone(mask ? maskPhone(user.getPhone()) : user.getPhone());
            dto.setStatus(user.getStatus());
            return dto;
        }).toList();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户数据", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), UserExcelDTO.class).sheet("用户").doWrite(rows);
    }

    /**
     * 导入用户：预检查重 → 分批事务插入 → 逐行错误收集。
     *
     * <p>批次4（R4-1.50）：原实现单事务 + 每行 exists+insert（2N 次 DB 往返、整批
     * 一个事务、与并发 create 撞唯一键时整批回滚）。现改为：先一次性批量查重收集
     * 重复行，再按 500 行/批插入（每批独立事务），失败行单独记录不整批回滚。
     * 返回 {@link ImportResult}：成功数 + 失败行明细（行号/用户名/原因）。
     */
    public ImportResult importUsers(MultipartFile file) throws IOException {
        List<UserExcelDTO> rows = EasyExcel.read(file.getInputStream())
                .head(UserExcelDTO.class)
                .sheet()
                .doReadSync();
        String defaultPassword = defaultPassword();
        // 1) 预检：收集无用户名行与重复用户名（批量查重，避免逐行 exists）
        List<String> importedUsernames = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            UserExcelDTO row = rows.get(i);
            String username = row.getUsername() == null ? null : row.getUsername().trim();
            if (username == null || username.isBlank()) {
                errors.add(new RowError(i + 1, null, "用户名为空"));
                continue;
            }
            if (!seen.add(username)) {
                errors.add(new RowError(i + 1, username, "文件内用户名重复"));
                continue;
            }
            importedUsernames.add(username);
        }
        // 批量查库中已存在的用户名（一次 IN 查询替代逐行 exists）
        if (!importedUsernames.isEmpty()) {
            List<SysUserDO> existing = userMapper.selectList(new LambdaQueryWrapper<SysUserDO>()
                    .in(SysUserDO::getUsername, importedUsernames));
            Set<String> existingNames = existing.stream()
                    .map(u -> u.getUsername() == null ? "" : u.getUsername().trim())
                    .collect(Collectors.toSet());
            errors.addAll(rows.stream()
                    .filter(r -> r.getUsername() != null && existingNames.contains(r.getUsername().trim()))
                    .map(r -> new RowError(rows.indexOf(r) + 1, r.getUsername().trim(), "用户名已存在"))
                    .toList());
        }
        // 2) 分批插入：每批 500 行独立事务，失败行单独记录
        int success = 0;
        List<SysUserDO> pending = new ArrayList<>();
        for (UserExcelDTO row : rows) {
            String username = row.getUsername() == null ? null : row.getUsername().trim();
            if (username == null || username.isBlank()
                    || errors.stream().anyMatch(e -> username.equals(e.username()))
                    || !seen.contains(username)) {
                continue; // 预检失败行跳过
            }
            // seen 保证文件内唯一；此处再排除库中已存在（预检已收集，直接跳过）
            SysUserDO user = new SysUserDO();
            checkTenantUserLimit();
            user.setTenantId(TenantContext.getTenantId());
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(defaultPassword));
            user.setNickname(row.getNickname());
            user.setEmail(row.getEmail());
            user.setPhone(row.getPhone());
            user.setStatus(row.getStatus() == null ? ENABLED : row.getStatus());
            pending.add(user);
            if (pending.size() >= BATCH_SIZE) {
                success += insertBatch(pending, errors);
                pending.clear();
            }
        }
        if (!pending.isEmpty()) {
            success += insertBatch(pending, errors);
        }
        return new ImportResult(success, errors);
    }

    /** 单批插入（独立事务）：捕获逐行唯一键冲突等异常，失败行记录原因不整批回滚。 */
    @Transactional
    protected int insertBatch(List<SysUserDO> batch, List<RowError> errors) {
        int success = 0;
        for (SysUserDO user : batch) {
            try {
                userMapper.insert(user);
                success++;
            } catch (Exception exception) { // noqa - 单行失败记录后继续
                String username = user.getUsername() == null ? "" : user.getUsername();
                int rowNo = errors.size() + 1;
                errors.add(new RowError(rowNo, username, exception.getMessage() == null
                        ? "插入失败" : exception.getMessage()));
            }
        }
        return success;
    }

    private static final int BATCH_SIZE = 500;

    /** 导入结果：成功数 + 失败行明细。 */
    public record ImportResult(int successCount, List<RowError> errors) {
    }

    /** 单行导入失败记录。 */
    public record RowError(int rowNo, String username, String reason) {
    }

    private String defaultPassword() {
        SysConfigDO config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, "sys.user.initPassword"));
        return config == null ? DEFAULT_INIT_PASSWORD : config.getConfigValue();
    }

    private void checkTenantUserLimit() {
        Long tenantId = TenantContext.getTenantId();
        SysTenantDO tenant = tenantMapper.selectOne(new LambdaQueryWrapper<SysTenantDO>()
                .eq(SysTenantDO::getId, tenantId)
                .last("FOR UPDATE"));
        if (tenant == null || tenant.getUserLimit() == null) {
            return;
        }
        long current = userMapper.selectCount(new LambdaQueryWrapper<SysUserDO>()
                .eq(SysUserDO::getTenantId, tenantId));
        if (current >= tenant.getUserLimit()) {
            throw new BusinessException(ResultCode.TENANT_LIMIT_EXCEEDED);
        }
    }
}

