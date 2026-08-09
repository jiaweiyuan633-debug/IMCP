package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.monitor.vo.DashboardStatsVo;
import com.example.admin.module.monitor.vo.OnlineUserVo;
import com.example.admin.module.system.entity.SysLoginLog;
import com.example.admin.module.system.entity.SysMenu;
import com.example.admin.module.system.entity.SysOperLog;
import com.example.admin.module.system.entity.SysRole;
import com.example.admin.module.system.entity.SysUser;
import com.example.admin.module.system.mapper.SysLoginLogMapper;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysOperLogMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.common.TenantContext;
import com.example.admin.security.TokenService;
import com.example.admin.module.system.DataScopeHelper;
import com.example.admin.module.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final TokenService tokenService;
    private final JdbcTemplate jdbcTemplate;
    private final DataScopeHelper dataScopeHelper;

    public PageResult<SysLoginLog> loginLogPage(long pageNum, long pageSize, String username) {
        Page<SysLoginLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .orderByDesc(SysLoginLog::getId);
        applyLoginLogScope(wrapper);
        IPage<SysLoginLog> result = loginLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public PageResult<SysOperLog> operLogPage(long pageNum, long pageSize, String module) {
        Page<SysOperLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(module), SysOperLog::getModule, module)
                .orderByDesc(SysOperLog::getId);
        applyUserIdScope(wrapper, SysOperLog::getUserId);
        IPage<SysOperLog> result = operLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public List<OnlineUserVo> onlineUsers() {
        return tokenService.listOnlineUsers();
    }

    public void kick(String tokenId) {
        tokenService.revokeAccessToken(tokenId);
    }

    public void clearCache(String key) {
        tokenService.deleteCacheKey(key);
    }

    public DashboardStatsVo stats() {
        Map<String, Object> aiTaskStats = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(status = 'SUCCEEDED'), 0) AS succeeded,
                       COALESCE(SUM(status = 'FAILED'), 0) AS failed,
                       COALESCE(SUM(status IN ('PENDING', 'QUEUED', 'RUNNING')), 0) AS running
                FROM ai_task
                """);
        return DashboardStatsVo.builder()
                .userCount(userMapper.selectCount(null))
                .roleCount(roleMapper.selectCount(null))
                .menuCount(menuMapper.selectCount(null))
                .loginLogCount(loginLogMapper.selectCount(null))
                .operLogCount(operLogMapper.selectCount(null))
                .aiTaskTotal(toLong(aiTaskStats.get("total")))
                .aiTaskSucceeded(toLong(aiTaskStats.get("succeeded")))
                .aiTaskFailed(toLong(aiTaskStats.get("failed")))
                .aiTaskRunning(toLong(aiTaskStats.get("running")))
                .build();
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private void applyLoginLogScope(LambdaQueryWrapper<SysLoginLog> wrapper) {
        if (dataScopeHelper.isAdmin()) {
            return;
        }
        List<Long> userIds = dataScopeHelper.allowedUserIds();
        if (userIds == null) {
            return;
        }
        List<String> usernames = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getId, userIds)
                        .eq(SysUser::getTenantId, TenantContext.getTenantId()))
                .stream()
                .map(SysUser::getUsername)
                .toList();
        wrapper.in(SysLoginLog::getUsername, usernames);
    }

    private void applyUserIdScope(LambdaQueryWrapper<SysOperLog> wrapper, com.baomidou.mybatisplus.core.toolkit.support.SFunction<SysOperLog, ?> column) {
        if (dataScopeHelper.isAdmin()) {
            return;
        }
        List<Long> userIds = dataScopeHelper.allowedUserIds();
        if (userIds == null) {
            return;
        }
        if (userIds.size() == 1) {
            wrapper.eq(column, userIds.get(0));
        } else {
            wrapper.in(column, userIds);
        }
    }
}

