package cn.admin.scaffold.module.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import cn.admin.scaffold.common.MessageBizType;
import cn.admin.scaffold.common.ScheduledTaskLock;
import cn.admin.scaffold.module.ai.entity.AiTaskDO;
import cn.admin.scaffold.module.ai.mapper.AiTaskMapper;
import cn.admin.scaffold.module.system.SystemMessageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskScannerTest {

    // 被扫方法内部会构造 LambdaUpdateWrapper<AiTaskDO>，需要 MyBatis-Plus 已初始化实体 TableInfo
    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiTaskDO.class);
    }

    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final SystemMessageService messageService = mock(SystemMessageService.class);
    private final ScheduledTaskLock scheduledTaskLock = mock(ScheduledTaskLock.class);
    private final AiTaskScanner scanner = new AiTaskScanner(taskMapper, messageService, scheduledTaskLock);

    private AiTaskDO timeoutTask() {
        AiTaskDO task = new AiTaskDO();
        task.setId(1L);
        task.setTenantId(1L);
        task.setTaskNo("T-001");
        task.setCreatedBy(10L);
        return task;
    }

    @Test
    void skipsScanWhenLockNotAcquired() {
        when(scheduledTaskLock.tryLock(anyString(), any(Duration.class))).thenReturn(false);
        scanner.scanTimeoutTasks();
        verify(taskMapper, never()).selectTenantIds();
        verify(scheduledTaskLock, never()).unlock(anyString());
    }

    @Test
    void marksTimeoutAndNotifiesWhenUpdateSucceeds() {
        when(scheduledTaskLock.tryLock(anyString(), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectTenantIds()).thenReturn(List.of(1L));
        when(taskMapper.selectTimeoutTasks(anyLong(), any())).thenReturn(List.of(timeoutTask()));
        when(taskMapper.update(any(), any())).thenReturn(1);

        scanner.scanTimeoutTasks();

        verify(messageService).sendSystemToUsers(
                eq(List.of(10L)), eq(1L), eq("AI 任务失败"), anyString(), eq(MessageBizType.AI), eq(1L));
    }

    @Test
    void skipsNotifyWhenConcurrentCallbackAlreadySettled() {
        // 回调已把任务置为终态（并发抢占），条件更新命中 0 行 -> 不再覆盖终态、不再重复通知
        when(scheduledTaskLock.tryLock(anyString(), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectTenantIds()).thenReturn(List.of(1L));
        when(taskMapper.selectTimeoutTasks(anyLong(), any())).thenReturn(List.of(timeoutTask()));
        when(taskMapper.update(any(), any())).thenReturn(0);

        scanner.scanTimeoutTasks();

        verify(messageService, never()).sendSystemToUsers(
                any(), anyLong(), anyString(), anyString(), any(), any());
    }
}
