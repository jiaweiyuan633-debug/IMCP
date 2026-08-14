package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.MessageBizType;
import com.example.admin.common.ScheduledTaskLock;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysProcessNodeDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流节点超时提醒扫描器（批次2 死代码转活）。
 *
 * <p>sys_workflow 的 current_node_assigned_at/timeout_notified 字段（V23 建表）此前无任何扫描器消费，
 * 属"字段已建、能力缺实现"。本扫描器每分钟检查审批中的流程：当前节点停留超过节点级
 * 超时阈值（sys_process_node.timeout_hours，缺省 48h）即向审批人与申请人发待办提醒，并置
 * timeout_notified=1 保证单节点只提醒一次；审批流转时由 {@code WarmFlowWorkflowService#afterAction}
 * 重置计时，下一节点重新计时。
 *
 * <p>多副本通过 {@link ScheduledTaskLock} 互斥，保证任一时刻仅一个实例扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowTimeoutScanner {

    private static final Duration SCAN_LOCK_TTL = Duration.ofMinutes(5);
    /** sys_process_node 无配置时的兜底阈值（与 V23 建表默认一致）。 */
    private static final int DEFAULT_TIMEOUT_HOURS = 48;

    private final SysWorkflowMapper workflowMapper;
    private final SysProcessNodeMapper processNodeMapper;
    private final SystemMessageService messageService;
    private final ScheduledTaskLock taskLock;

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void scan() {
        if (!taskLock.tryLock("workflow-timeout-scan", SCAN_LOCK_TTL)) {
            return;
        }
        try {
            // R4-1.29：@Scheduled 线程无租户上下文，直接 selectList 会被租户拦截器注入默认
            // tenant_id=1，仅能扫到租户 1 的超时流程、其余租户提醒永不触发（同款缺陷见
            // AiTaskScanner#scanTimeoutTasks 修复）。改为跨租户取全量租户，逐个就位上下文后扫描，
            // 使 selectList/resolveTimeoutHours/updateById 全部按对应租户精确限定。
            List<Long> tenantIds = workflowMapper.selectTenantIds();
            for (Long tenantId : tenantIds) {
                TenantContext.setTenantId(tenantId);
                try {
                    scanPendingForTenant();
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            taskLock.unlock("workflow-timeout-scan");
        }
    }

    /** 当前租户上下文就位下扫描本租户超时流程（租户拦截器按 TenantContext 自动限定查询与更新）。 */
    private void scanPendingForTenant() {
        List<SysWorkflowDO> pending = workflowMapper.selectList(new LambdaQueryWrapper<SysWorkflowDO>()
                .eq(SysWorkflowDO::getStatus, WorkflowStatus.PENDING.name())
                .eq(SysWorkflowDO::getTimeoutNotified, 0)
                .isNotNull(SysWorkflowDO::getCurrentNodeAssignedAt)
                .last("LIMIT 100"));
        if (pending.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SysWorkflowDO workflow : pending) {
            int timeoutHours = resolveTimeoutHours(workflow);
            if (workflow.getCurrentNodeAssignedAt().plusHours(timeoutHours).isAfter(now)) {
                continue;
            }
            notifyTimeout(workflow, timeoutHours);
            workflow.setTimeoutNotified(1);
            workflowMapper.updateById(workflow);
            log.info("工作流节点超时提醒已发送: workflowId={}, node={}, timeoutHours={}",
                    workflow.getId(), workflow.getCurrentNodeName(), timeoutHours);
        }
    }

    /** 取当前节点超时阈值：按 processDefId + 节点名匹配 sys_process_node，缺省 48h。 */
    private int resolveTimeoutHours(SysWorkflowDO workflow) {
        SysProcessNodeDO node = processNodeMapper.selectOne(new LambdaQueryWrapper<SysProcessNodeDO>()
                .eq(SysProcessNodeDO::getProcessDefId, workflow.getProcessDefId())
                .eq(SysProcessNodeDO::getNodeName, workflow.getCurrentNodeName())
                .last("LIMIT 1"));
        return node == null || node.getTimeoutHours() == null ? DEFAULT_TIMEOUT_HOURS : node.getTimeoutHours();
    }

    private void notifyTimeout(SysWorkflowDO workflow, int timeoutHours) {
        List<Long> targets = new ArrayList<>();
        if (workflow.getAssigneeUserId() != null) {
            targets.add(workflow.getAssigneeUserId());
        }
        if (workflow.getApplicantId() != null && !targets.contains(workflow.getApplicantId())) {
            targets.add(workflow.getApplicantId());
        }
        if (targets.isEmpty()) {
            return;
        }
        messageService.sendTodoToUsers(targets, workflow.getTenantId(),
                "流程节点超时提醒",
                "流程「" + workflow.getProcessName() + "」当前节点「" + workflow.getCurrentNodeName()
                        + "」已超过 " + timeoutHours + " 小时未处理，请及时跟进。",
                MessageBizType.WORKFLOW, workflow.getId());
    }
}
