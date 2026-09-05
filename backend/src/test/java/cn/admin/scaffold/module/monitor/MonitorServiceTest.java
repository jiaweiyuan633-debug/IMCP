package cn.admin.scaffold.module.monitor;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import cn.admin.scaffold.module.ai.mapper.AiTaskMapper;
import cn.admin.scaffold.module.monitor.vo.DashboardStatsVo;
import cn.admin.scaffold.module.report.vo.NameValueVo;
import cn.admin.scaffold.module.system.mapper.SysLoginLogMapper;
import cn.admin.scaffold.module.system.mapper.SysMenuMapper;
import cn.admin.scaffold.module.system.mapper.SysOperLogMapper;
import cn.admin.scaffold.module.system.mapper.SysRoleMapper;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import cn.admin.scaffold.security.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    private SysLoginLogMapper loginLogMapper;
    @Mock
    private SysOperLogMapper operLogMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private TokenService tokenService;
    @Mock
    private AiTaskMapper aiTaskMapper;

    @InjectMocks
    private MonitorService service;

    // ---------- AI 任务失败分类(error_type)分层统计 ----------

    @Test
    void statsGroupsFailedTasksByErrorType() {
        // selectCount(Wrapper)：SUCCEEDED=70、FAILED=6、RUNNING=5（调用顺序与 stats() 一致）
        AtomicInteger wrapperIdx = new AtomicInteger();
        // stats() 里 selectCount(null) 有 6 处（全量计数），先显式 stub 其默认值，避免 strict 模式把字面 null 与 argThat(非null) 判为参数不匹配
        when(aiTaskMapper.selectCount(isNull())).thenReturn(0L);
        // 3 个 status 条件 wrapper 按调用顺序返回 SUCCEEDED=70、FAILED=6、RUNNING=5
        when(aiTaskMapper.selectCount(argThat(wrapper -> wrapper != null))).thenAnswer(invocation -> switch (wrapperIdx.getAndIncrement()) {
            case 0 -> 70L;
            case 1 -> 6L;
            case 2 -> 5L;
            default -> 0L;
        });
        // selectMaps 分组行：timeout=3、non_retryable=2、error_type 为空=1（→ other 兜底桶）
        when(aiTaskMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(
                Map.<String, Object>of("name", "timeout", "value", 3L),
                Map.<String, Object>of("name", "non_retryable", "value", 2L),
                Map.<String, Object>of("name", "", "value", 1L)
        ));

        DashboardStatsVo vo = service.stats();

        assertEquals(6L, vo.getAiTaskFailed());
        List<NameValueVo> byType = vo.getAiTaskFailedByErrorType();
        assertNotNull(byType);
        assertEquals(3, byType.size());
        // 已知分类按失败数降序，other 兜底桶排最后
        assertEquals("timeout", byType.get(0).getName());
        assertEquals(3L, byType.get(0).getValue());
        assertEquals("non_retryable", byType.get(1).getName());
        assertEquals(2L, byType.get(1).getValue());
        assertEquals("other", byType.get(2).getName());
        assertEquals(1L, byType.get(2).getValue());
    }

    @Test
    void statsReturnsEmptyErrorTypeListWhenNoFailedTasks() {
        when(aiTaskMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());

        DashboardStatsVo vo = service.stats();

        assertNotNull(vo.getAiTaskFailedByErrorType());
        assertTrue(vo.getAiTaskFailedByErrorType().isEmpty());
    }
}
