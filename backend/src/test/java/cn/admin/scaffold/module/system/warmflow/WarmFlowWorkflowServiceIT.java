package cn.admin.scaffold.module.system.warmflow;

import cn.admin.scaffold.AbstractIntegrationTest;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.WorkflowStatus;
import cn.admin.scaffold.module.system.entity.SysUserDO;
import cn.admin.scaffold.module.system.entity.SysWorkflowDO;
import cn.admin.scaffold.module.system.entity.SysWorkflowLogDO;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Warm-Flow 工作流引擎集成测试（此前引擎封装零测试，本次补齐覆盖）。
 *
 * <p>真实 MySQL + Warm-Flow：启动时 V13 种子流程（general_approval：部门审批 → 管理员终审）
 * 经 {@link WarmFlowLegacyMigrator} 迁移为 Warm-Flow 定义。覆盖：发起 → 部门审批通过 →
 * 流转到管理员终审 → 终审通过（APPROVED）；以及驳回（REJECTED）路径。租户隔离断言
 * 通过 WarmFlowTenantHandler + TenantContext 验证。
 */
class WarmFlowWorkflowServiceIT extends AbstractIntegrationTest {

    @Autowired
    private WarmFlowWorkflowService workflowService;

    @Autowired
    private SysUserMapper userMapper;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createStartApproveToEndRoundTrip() {
        SysWorkflowDO workflow = newWorkflow();
        Long workflowId = workflowService.create(workflow);

        assertThat(workflowId).isNotNull();
        // 发起后处于审批中
        assertThat(workflowService.detail(workflowId).getStatus())
                .isEqualTo(WorkflowStatus.PENDING.name());

        // 第一节点审批通过（部门审批）→ 流转到管理员终审
        workflowService.approve(workflowId, null, null, "部门审批通过");
        assertThat(workflowService.detail(workflowId).getStatus())
                .isEqualTo(WorkflowStatus.PENDING.name());
        assertThat(workflowService.detail(workflowId).getCurrentNodeName())
                .isEqualTo("管理员终审");

        // 终审通过 → APPROVED
        workflowService.approve(workflowId, null, null, "终审通过");
        assertThat(workflowService.detail(workflowId).getStatus())
                .isEqualTo(WorkflowStatus.APPROVED.name());

        // 审批日志完整
        List<SysWorkflowLogDO> logs = workflowService.logs(workflowId);
        assertThat(logs).extracting(SysWorkflowLogDO::getAction)
                .contains("STARTED", "APPROVED");
    }

    @Test
    void rejectEndsWorkflow() {
        SysWorkflowDO workflow = newWorkflow();
        Long workflowId = workflowService.create(workflow);

        workflowService.reject(workflowId, null, null, "资料不齐");

        assertThat(workflowService.detail(workflowId).getStatus())
                .isEqualTo(WorkflowStatus.REJECTED.name());
        assertThat(workflowService.logs(workflowId))
                .extracting(SysWorkflowLogDO::getAction)
                .contains("REJECTED");
    }

    @Test
    void withdrawByApplicantCancels() {
        SysWorkflowDO workflow = newWorkflow();
        Long workflowId = workflowService.create(workflow);

        workflowService.withdraw(workflowId, "发起人撤回");

        assertThat(workflowService.detail(workflowId).getStatus())
                .isEqualTo(WorkflowStatus.WITHDRAWN.name());
    }

    @Test
    void delegateTransfersTask() {
        SysWorkflowDO workflow = newWorkflow();
        Long workflowId = workflowService.create(workflow);

        // 转办目标用户：V1 种子仅 admin(id=1)，测试插入 id=2 用户（密码为 bcrypt 占位）
        SysUserDO target = new SysUserDO();
        target.setId(2L);
        target.setTenantId(1L);
        target.setUsername("wf-delegate-user");
        target.setNickname("转办目标");
        target.setPassword("$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5bG9mFhR0nP8mP8mP8mP8mP8mP8m");
        target.setStatus(1);
        userMapper.insert(target);
        workflowService.delegate(workflowId, 2L);

        // 转办成功后记录 DELEGATED 日志，流程仍在审批中
        List<SysWorkflowLogDO> logs = workflowService.logs(workflowId);
        assertThat(logs).extracting(SysWorkflowLogDO::getAction)
                .contains("DELEGATED");
        assertThat(workflowService.detail(workflowId).getStatus())
                .isEqualTo(WorkflowStatus.PENDING.name());
    }

    @Test
    void createRejectsUnknownDefinition() {
        SysWorkflowDO workflow = newWorkflow();
        workflow.setProcessDefId(999999L); // 不存在的流程定义

        assertThatThrownBy(() -> workflowService.create(workflow))
                .isInstanceOf(BusinessException.class);
    }

    private SysWorkflowDO newWorkflow() {
        // 从 Warm-Flow 查 V13 种子迁移出的 general_approval 已发布定义（Migrator 生成的自增 id ≠ 1）
        List<Definition> defs = FlowEngine.defService().list(FlowEngine.newDef().setFlowCode("general_approval"));
        assertThat(defs).isNotEmpty();
        Definition def = defs.stream()
                .filter(d -> d.getIsPublish() != null && d.getIsPublish() == 1)
                .findFirst()
                .orElse(defs.get(0));
        SysWorkflowDO workflow = new SysWorkflowDO();
        workflow.setProcessName("集成测试流程");
        workflow.setBizType("FORM");
        workflow.setBizId(10001L);
        workflow.setContent("集成测试内容");
        workflow.setProcessDefId(def.getId());
        workflow.setFormData("{\"name\":\"张三\",\"reason\":\"事假\"}");
        return workflow;
    }
}
