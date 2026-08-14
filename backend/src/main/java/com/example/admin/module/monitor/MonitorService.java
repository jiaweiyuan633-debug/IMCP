package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.monitor.vo.DashboardStatsVo;
import com.example.admin.module.monitor.vo.OnlineUserVo;
import com.example.admin.module.system.entity.SysLoginLogDO;
import com.example.admin.module.system.entity.SysMenuDO;
import com.example.admin.module.system.entity.SysOperLogDO;
import com.example.admin.module.system.entity.SysRoleDO;
import com.example.admin.module.ai.entity.AiTaskDO;
import com.example.admin.module.ai.AiTaskStatus;
import com.example.admin.module.ai.mapper.AiTaskMapper;
import com.example.admin.module.system.mapper.SysLoginLogMapper;
import com.example.admin.module.system.mapper.SysMenuMapper;
import com.example.admin.module.system.mapper.SysOperLogMapper;
import com.example.admin.module.system.mapper.SysRoleMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.security.TokenService;
import com.example.admin.common.annotation.DataScope;
import com.example.admin.module.report.vo.NameValueVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final AiTaskMapper aiTaskMapper;

    @DataScope(tables = {"sys_login_log"})
    public PageResult<SysLoginLogDO> loginLogPage(long pageNum, long pageSize, String username) {
        Page<SysLoginLogDO> page = new Page<>(pageNum, pageSize, false);
        LambdaQueryWrapper<SysLoginLogDO> wrapper = new LambdaQueryWrapper<SysLoginLogDO>()
                .like(StringUtils.hasText(username), SysLoginLogDO::getUsername, username)
                .orderByDesc(SysLoginLogDO::getId);
        IPage<SysLoginLogDO> result = loginLogMapper.selectPage(page, wrapper);
        page.setTotal(loginLogMapper.selectCount(wrapper));
        return PageResult.of(result, result.getRecords());
    }

    @DataScope(tables = {"sys_oper_log"})
    public PageResult<SysOperLogDO> operLogPage(long pageNum, long pageSize, String module) {
        Page<SysOperLogDO> page = new Page<>(pageNum, pageSize, false);
        LambdaQueryWrapper<SysOperLogDO> wrapper = new LambdaQueryWrapper<SysOperLogDO>()
                .like(StringUtils.hasText(module), SysOperLogDO::getModule, module)
                .orderByDesc(SysOperLogDO::getId);
        IPage<SysOperLogDO> result = operLogMapper.selectPage(page, wrapper);
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
        long aiSucceeded = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTaskDO>()
                .eq(AiTaskDO::getStatus, AiTaskStatus.SUCCEEDED.name()));
        long aiFailed = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTaskDO>()
                .eq(AiTaskDO::getStatus, AiTaskStatus.FAILED.name()));
        long aiRunning = aiTaskMapper.selectCount(new LambdaQueryWrapper<AiTaskDO>()
                .in(AiTaskDO::getStatus,
                        AiTaskStatus.PENDING.name(),
                        AiTaskStatus.QUEUED.name(),
                        AiTaskStatus.RUNNING.name()));
        // R4-1.26：FAILED 任务按 error_type 分组，供大屏饼图区分失败构成（超时/不可重试/重试耗尽）。
        // selectMaps 的 GROUP BY 同样被 @DataScope 数据权限拦截器改写，与上方 selectCount 口径一致。
        // error_type 为空（历史数据 / AiTaskScanner 兜底置 FAILED 未写 error_type）归入 "other" 桶，保证各桶之和等于 aiTaskFailed。
        List<NameValueVo> failedByErrorType = new ArrayList<>();
        for (Map<String, Object> row : aiTaskMapper.selectMaps(new QueryWrapper<AiTaskDO>()
                .select("error_type AS name", "COUNT(*) AS value")
                .eq("status", AiTaskStatus.FAILED.name())
                .groupBy("error_type"))) {
            String name = readMapColumn(row, "name");
            if (!StringUtils.hasText(name)) {
                name = ERROR_TYPE_OTHER;
            }
            failedByErrorType.add(new NameValueVo(name, readMapLong(row, "value")));
        }
        // 已知分类按失败数降序，"other" 兜底桶固定排最后（饼图图例顺序友好）
        failedByErrorType.sort((a, b) -> {
            boolean aOther = ERROR_TYPE_OTHER.equals(a.getName());
            boolean bOther = ERROR_TYPE_OTHER.equals(b.getName());
            if (aOther != bOther) {
                return aOther ? 1 : -1;
            }
            return Long.compare(b.getValue(), a.getValue());
        });
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
                .aiTaskFailedByErrorType(failedByErrorType)
                .build();
    }

    /** 兜底分类：error_type 为空的失败任务（历史数据 / AiTaskScanner 兜底置 FAILED 未写 error_type）。 */
    private static final String ERROR_TYPE_OTHER = "other";

    /** 读取 selectMaps 行内列值，兼容别名大小写（不同数据库返回的列标签大小写可能不同）。 */
    private static String readMapColumn(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static long readMapLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

}

