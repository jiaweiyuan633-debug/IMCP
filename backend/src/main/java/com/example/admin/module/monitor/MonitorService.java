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
import com.example.admin.module.ai.entity.AiTask;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.module.system.mapper.SysLoginLogMapper;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysOperLogMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.security.TokenService;
import com.example.admin.common.annotation.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final TokenService tokenService;
    private final AiTaskMapper aiTaskMapper;

    @DataScope(tables = {"sys_login_log"})
    public PageResult<SysLoginLog> loginLogPage(long pageNum, long pageSize, String username) {
        Page<SysLoginLog> page = new Page<>(pageNum, pageSize, false);
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .orderByDesc(SysLoginLog::getId);
        IPage<SysLoginLog> result = loginLogMapper.selectPage(page, wrapper);
        page.setTotal(loginLogMapper.selectCount(wrapper));
        return PageResult.of(result, result.getRecords());
    }

    @DataScope(tables = {"sys_oper_log"})
    public PageResult<SysOperLog> operLogPage(long pageNum, long pageSize, String module) {
        Page<SysOperLog> page = new Page<>(pageNum, pageSize, false);
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .like(StringUtils.hasText(module), SysOperLog::getModule, module)
                .orderByDesc(SysOperLog::getId);
        IPage<SysOperLog> result = operLogMapper.selectPage(page, wrapper);
        page.setTotal(operLogMapper.selectCount(wrapper));
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

    @DataScope(tables = {"sys_user", "sys_login_log", "sys_oper_log", "ai_task"})
    public DashboardStatsVo stats() {
        long aiTotal = aiTaskMapper.selectCount(null);
        long aiSucceeded = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getStatus, "SUCCEEDED"));
        long aiFailed = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getStatus, "FAILED"));
        long aiRunning = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTask>()
                .in(AiTask::getStatus, "PENDING", "QUEUED", "RUNNING"));
        return DashboardStatsVo.builder()
                .userCount(userMapper.selectCount(null))
                .roleCount(roleMapper.selectCount(null))
                .menuCount(menuMapper.selectCount(null))
                .loginLogCount(loginLogMapper.selectCount(null))
                .operLogCount(operLogMapper.selectCount(null))
                .aiTaskTotal(aiTotal)
                .aiTaskSucceeded(aiSucceeded)
                .aiTaskFailed(aiFailed)
                .aiTaskRunning(aiRunning)
                .build();
    }

}

