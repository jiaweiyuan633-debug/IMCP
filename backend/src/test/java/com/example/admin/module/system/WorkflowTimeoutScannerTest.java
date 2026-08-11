package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.admin.common.MessageBizType;
import com.example.admin.common.ScheduledTaskLock;
import com.example.admin.module.system.entity.SysProcessNodeDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTimeoutScannerTest {

    // 被扫方法内部会构造 LambdaQueryWrapper<SysWorkflowDO>/<SysProcessNodeDO>，
    // 需要 MyBatis-Plus 已初始化对应实体 TableInfo
    static {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysWorkflowDO.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysProcessNodeDO.class);
    }

    private final SysWorkflowMapper workflowMapper = mock(SysWorkflowMapper.class);
    private final SysProcessNodeMapper processNodeMapper = mock(SysProcessNodeMapper.class);
    private final SystemMessageService messageService = mock(SystemMessageService.class);
    private final ScheduledTaskLock taskLock = mock(ScheduledTaskLock.class);
    private final WorkflowTimeoutScanner scanner =
            new WorkflowTimeoutScanner(workflowMapper, processNodeMapper, messageService, taskLock);

    private SysWorkflowDO workflow;

    @BeforeEach
    void setUp() {
        when(taskLock.tryLock(anyString(), any(Duration.class))).thenReturn(true);
        workflow = new SysWorkflowDO();
        workflow.setId(100L);
        workflow.setTenantId(1L);
        workflow.setProcessDefId(9L);
        workflow.setProcessName("请假审批");
        workflow.setCurrentNodeName("部门经理审批");
        workflow.setCurrentNodeAssignedAt(LocalDateTime.now().minusHours(5));
        workflow.setStatus(WorkflowStatus.PENDING.name());
        workflow.setTimeoutNotified(0);
        workflow.setAssigneeUserId(10L);
        workflow.setApplicantId(11L);
    }

    private SysProcessNodeDO node(int timeoutHours) {
        SysProcessNodeDO node = new SysProcessNodeDO();
        node.setProcessDefId(9L);
        node.setNodeName("部门经理审批");
        node.setTimeoutHours(timeoutHours);
        return node;
    }

    @Test
    void skipsScanWhenLockNotAcquired() {
        when(taskLock.tryLock(anyString(), any(Duration.class))).thenReturn(false);
        scanner.scan();
        verify(workflowMapper, never()).selectList(any());
        verify(taskLock, never()).unlock(anyString());
    }

    @Test
    void notifiesAndMarksNotifiedWhenNodeExceeded() {
        when(workflowMapper.selectList(any())).thenReturn(List.of(workflow));
        when(processNodeMapper.selectOne(any())).thenReturn(node(2));

        scanner.scan();

        verify(messageService).sendTodoToUsers(
                eq(List.of(10L, 11L)), eq(1L), eq("流程节点超时提醒"), anyString(),
                eq(MessageBizType.WORKFLOW), eq(100L));
        ArgumentCaptor<SysWorkflowDO> captor = ArgumentCaptor.forClass(SysWorkflowDO.class);
        verify(workflowMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTimeoutNotified()).isEqualTo(1);
    }

    @Test
    void doesNotNotifyWhenWithinNodeTimeout() {
        workflow.setCurrentNodeAssignedAt(LocalDateTime.now().minusHours(1));
        when(workflowMapper.selectList(any())).thenReturn(List.of(workflow));
        when(processNodeMapper.selectOne(any())).thenReturn(node(2));

        scanner.scan();

        verify(messageService, never()).sendTodoToUsers(any(), any(), any(), any(), any(), any());
        verify(workflowMapper, never()).updateById(any(SysWorkflowDO.class));
    }

    @Test
    void fallsBackToDefaultTimeoutWhenNodeNotConfigured() {
        // 节点未配置超时阈值 -> 兜底 48h，停留 49h 视为超时
        workflow.setCurrentNodeAssignedAt(LocalDateTime.now().minusHours(49));
        when(workflowMapper.selectList(any())).thenReturn(List.of(workflow));
        when(processNodeMapper.selectOne(any())).thenReturn(null);

        scanner.scan();

        verify(messageService).sendTodoToUsers(any(), any(), any(), anyString(),
                eq(MessageBizType.WORKFLOW), eq(100L));
    }

    @Test
    void deduplicatesAssigneeWhenApplicantIsSamePerson() {
        workflow.setApplicantId(10L);
        when(workflowMapper.selectList(any())).thenReturn(List.of(workflow));
        when(processNodeMapper.selectOne(any())).thenReturn(node(2));

        scanner.scan();

        verify(messageService, times(1)).sendTodoToUsers(any(), any(), any(), any(), any(), any());
    }
}
