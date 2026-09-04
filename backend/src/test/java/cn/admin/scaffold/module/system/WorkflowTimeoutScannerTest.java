package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.MessageBizType;
import cn.admin.scaffold.common.ScheduledTaskLock;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.entity.SysProcessNodeDO;
import cn.admin.scaffold.module.system.entity.SysWorkflowDO;
import cn.admin.scaffold.module.system.mapper.SysProcessNodeMapper;
import cn.admin.scaffold.module.system.mapper.SysWorkflowMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R4-1.29：工作流超时扫描器多租户回归。修复前 @Scheduled 线程无租户上下文，selectList 被
 * 租户拦截器注入默认 tenant_id=1，仅扫到租户 1；修复后按 selectTenantIds 逐租户就位上下文，
 * 断言 updateById 时 ThreadLocal 恒等于流程归属租户（租户 2 的提醒可正常触发）。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTimeoutScannerTest {

    @Mock
    private SysWorkflowMapper workflowMapper;
    @Mock
    private SysProcessNodeMapper processNodeMapper;
    @Mock
    private SystemMessageService messageService;
    @Mock
    private ScheduledTaskLock taskLock;

    @InjectMocks
    private WorkflowTimeoutScanner scanner;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private SysWorkflowDO pendingWorkflow(long id, long tenantId, LocalDateTime assignedAt) {
        SysWorkflowDO workflow = new SysWorkflowDO();
        workflow.setId(id);
        workflow.setTenantId(tenantId);
        workflow.setProcessName("审批流程-" + id);
        workflow.setProcessDefId(9L);
        workflow.setCurrentNodeName("部门审批");
        workflow.setCurrentNodeAssignedAt(assignedAt);
        workflow.setTimeoutNotified(0);
        workflow.setStatus(WorkflowStatus.PENDING.name());
        workflow.setAssigneeUserId(100L);
        workflow.setApplicantId(101L);
        return workflow;
    }

    @Test
    void scansEachTenantWithTenantContextBound() {
        // 修复前：selectList 无租户上下文被注入默认 tenant_id=1，租户 2 的超时流程永不扫描。
        // 修复后：跨租户取租户列表逐个就位上下文，updateById 时 ThreadLocal 必须等于流程归属租户。
        when(taskLock.tryLock(eq("workflow-timeout-scan"), any())).thenReturn(true);
        when(workflowMapper.selectTenantIds()).thenReturn(List.of(1L, 2L));
        SysWorkflowDO w1 = pendingWorkflow(1L, 1L, LocalDateTime.now().minusHours(49));
        SysWorkflowDO w2 = pendingWorkflow(2L, 2L, LocalDateTime.now().minusHours(49));
        when(workflowMapper.selectList(any())).thenReturn(List.of(w1), List.of(w2));
        // 无节点配置 → 默认 48h 阈值，assignAt 49h 前必超时
        Map<Long, Long> tenantAtUpdate = new HashMap<>();
        when(workflowMapper.updateById(any(SysWorkflowDO.class))).thenAnswer(invocation -> {
            SysWorkflowDO workflow = invocation.getArgument(0);
            tenantAtUpdate.put(workflow.getTenantId(), TenantContext.getTenantId());
            return 1;
        });

        scanner.scan();

        assertThat(tenantAtUpdate).containsEntry(1L, 1L).containsEntry(2L, 2L);
        verify(messageService).sendTodoToUsers(anyList(), eq(1L), anyString(), anyString(), anyString(), any());
        verify(messageService).sendTodoToUsers(anyList(), eq(2L), anyString(), anyString(), anyString(), any());
    }

    @Test
    void skipsWorkflowWithinDefaultTimeoutWindow() {
        when(taskLock.tryLock(eq("workflow-timeout-scan"), any())).thenReturn(true);
        when(workflowMapper.selectTenantIds()).thenReturn(List.of(1L));
        // assignAt 1 小时前，未达默认 48h 阈值 → 跳过，不更新不通知
        when(workflowMapper.selectList(any()))
                .thenReturn(List.of(pendingWorkflow(1L, 1L, LocalDateTime.now().minusHours(1))));

        scanner.scan();

        verify(workflowMapper, never()).updateById(any(SysWorkflowDO.class));
        verifyNoInteractions(messageService);
    }

    @Test
    void usesNodeSpecificTimeoutHours() {
        // 节点配置 timeout_hours=1，assignAt 2 小时前 → 触发超时（默认 48h 阈值下本不会触发）
        when(taskLock.tryLock(eq("workflow-timeout-scan"), any())).thenReturn(true);
        when(workflowMapper.selectTenantIds()).thenReturn(List.of(1L));
        when(workflowMapper.selectList(any()))
                .thenReturn(List.of(pendingWorkflow(1L, 1L, LocalDateTime.now().minusHours(2))));
        SysProcessNodeDO node = new SysProcessNodeDO();
        node.setProcessDefId(9L);
        node.setNodeName("部门审批");
        node.setTimeoutHours(1);
        when(processNodeMapper.selectOne(any())).thenReturn(node);

        scanner.scan();

        verify(workflowMapper).updateById(any(SysWorkflowDO.class));
        verify(messageService).sendTodoToUsers(anyList(), eq(1L), anyString(), anyString(),
                eq(MessageBizType.WORKFLOW), any());
    }

    @Test
    void doesNothingWhenLockUnavailable() {
        when(taskLock.tryLock(eq("workflow-timeout-scan"), any())).thenReturn(false);

        scanner.scan();

        verifyNoInteractions(workflowMapper, processNodeMapper, messageService);
    }
}
