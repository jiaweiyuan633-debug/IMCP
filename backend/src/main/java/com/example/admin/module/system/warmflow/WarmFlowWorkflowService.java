package com.example.admin.module.system.warmflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.MessageBizType;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.SystemMessageService;
import com.example.admin.module.system.WorkflowStatus;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
import com.example.admin.module.system.entity.SysWorkflowLogDO;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysWorkflowLogMapper;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.utils.page.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarmFlowWorkflowService {

    private static final String USER_PREFIX = "user:";
    private static final String ALL_PERMISSION = "all";
    private static final String ADMIN_PERMISSION = "admin";

    private final SysWorkflowMapper workflowMapper;
    private final SysWorkflowLogMapper workflowLogMapper;
    private final SysUserMapper userMapper;
    private final SystemMessageService messageService;
    private final ObjectMapper objectMapper;

    public PageResult<SysWorkflowDO> page(long pageNum, long pageSize, String status, String processName,
                                          String bizType, Long applicantId, Long defId) {
        Instance query = FlowEngine.newIns();
        if (StringUtils.hasText(status)) {
            query.setFlowStatus(toWarmStatus(status));
        }
        List<SysWorkflowDO> all = FlowEngine.insService().list(query).stream()
                .map(this::toWorkflowVO)
                .filter(Objects::nonNull)
                .filter(vo -> !StringUtils.hasText(processName) || contains(vo.getProcessName(), processName))
                .filter(vo -> !StringUtils.hasText(bizType) || contains(vo.getBizType(), bizType))
                .filter(vo -> applicantId == null || applicantId.equals(vo.getApplicantId()))
                .filter(vo -> defId == null || defId.equals(vo.getFlowDefId()))
                .sorted(Comparator.comparing(SysWorkflowDO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), pageNum, pageSize);
    }

    public PageResult<SysWorkflowDO> taskPage(long pageNum, long pageSize, String processName) {
        LoginUser user = SecurityUtils.getLoginUser();
        String handler = USER_PREFIX + user.getUserId();
        Set<Long> taskIds = new HashSet<>();
        collectTaskIds(handler, taskIds);
        collectTaskIds(ALL_PERMISSION, taskIds);
        if (isAdmin(user)) {
            collectTaskIds(ADMIN_PERMISSION, taskIds);
        }
        if (taskIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }
        List<Task> tasks = FlowEngine.taskService().getByIds(new ArrayList<>(taskIds)).stream()
                .filter(task -> FlowStatus.APPROVAL.getKey().equals(task.getFlowStatus()))
                .filter(task -> {
                    if (!StringUtils.hasText(processName)) {
                        return true;
                    }
                    SysWorkflowDO vo = toWorkflowVO(FlowEngine.insService().getById(task.getInstanceId()));
                    return vo != null && contains(vo.getProcessName(), processName);
                })
                .sorted(Comparator.comparing(Task::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = (int) Math.min((pageNum - 1) * pageSize, tasks.size());
        int to = (int) Math.min(from + pageSize, tasks.size());
        List<SysWorkflowDO> records = tasks.subList(from, to).stream()
                .map(task -> {
                    SysWorkflowDO vo = toWorkflowVO(FlowEngine.insService().getById(task.getInstanceId()));
                    if (vo == null) {
                        return null;
                    }
                    vo.setCurrentTaskId(task.getId());
                    vo.setCurrentNodeName(task.getNodeName());
                    return vo;
                })
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(records, tasks.size(), pageNum, pageSize);
    }

    @Transactional
    public Long create(SysWorkflowDO workflow) {
        if (workflow.getProcessDefId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择流程定义");
        }
        Definition definition = FlowEngine.defService().getById(workflow.getProcessDefId());
        if (definition == null || definition.getIsPublish() == null || definition.getIsPublish() != 1) {
            throw new BusinessException(ResultCode.WORKFLOW_DEF_INVALID);
        }
        LoginUser user = SecurityUtils.getLoginUser();
        workflow.setId(null);
        workflow.setProcessDefId(definition.getId());
        workflow.setFlowDefId(definition.getId());
        workflow.setApplicantId(user.getUserId());
        workflow.setApplicantName(user.getUsername());
        workflow.setStatus(WorkflowStatus.PENDING.name());
        workflow.setTenantId(TenantContext.getTenantId());
        workflow.setCurrentNodeAssignedAt(LocalDateTime.now());
        workflow.setTimeoutNotified(0);
        workflowMapper.insert(workflow);

        Map<String, Object> form = parseForm(workflow.getFormData());
        Map<String, Object> variables = new HashMap<>(form);
        FlowParams flowParams = FlowParams.build()
                .flowCode(definition.getFlowCode())
                .handler(String.valueOf(user.getUserId()))
                .variable(variables)
                .formData(form);
        Instance instance = FlowEngine.insService().start(String.valueOf(workflow.getId()), flowParams);
        workflow.setFlowInstanceId(instance.getId());
        workflow.setCurrentNodeName(instance.getNodeName());
        workflowMapper.updateById(workflow);
        saveLog(workflow.getId(), "STARTED", "发起流程：" + definition.getFlowName());
        notifyTaskUsers(instance.getId(), workflow.getTenantId(), "流程待办",
                "您有一条流程待办：「" + workflow.getProcessName() + "」等待审批。", workflow.getId());
        return workflow.getId();
    }

    @Transactional
    public void approve(Long id, Long taskId, Long nodeId, String remark) {
        SysWorkflowDO workflow = getOrThrow(id);
        Long resolvedTaskId = resolveTaskId(workflow, taskId, nodeId);
        Instance instance = FlowEngine.taskService().pass(resolvedTaskId, remark, Map.of());
        afterAction(workflow, instance, "APPROVED", remark == null ? "审批通过" : remark);
    }

    @Transactional
    public void reject(Long id, Long taskId, Long nodeId, String remark) {
        SysWorkflowDO workflow = getOrThrow(id);
        Long resolvedTaskId = resolveTaskId(workflow, taskId, nodeId);
        FlowParams flowParams = FlowParams.build()
                .flowStatus(FlowStatus.REJECT.getKey())
                .message(remark);
        Instance instance = FlowEngine.taskService().termination(resolvedTaskId, flowParams);
        afterAction(workflow, instance, "REJECTED", remark == null ? "审批拒绝" : remark);
    }

    @Transactional
    public void withdraw(Long id, String remark) {
        SysWorkflowDO workflow = getOrThrow(id);
        LoginUser user = SecurityUtils.getLoginUser();
        if (!isAdmin(user) && !user.getUserId().equals(workflow.getApplicantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        FlowParams flowParams = FlowParams.build()
                .handler(String.valueOf(user.getUserId()))
                .flowStatus(FlowStatus.CANCEL.getKey())
                .ignore(true);
        FlowEngine.taskService().terminationByInsId(workflow.getFlowInstanceId(), flowParams);
        workflow.setStatus(WorkflowStatus.WITHDRAWN.name());
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
        saveLog(id, "WITHDRAWN", remark == null ? "发起人撤回" : remark);
    }

    @Transactional
    public void delegate(Long id, Long delegateUserId) {
        SysWorkflowDO workflow = getOrThrow(id);
        SysUserDO target = userMapper.selectById(delegateUserId);
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        Long taskId = resolveTaskId(workflow, null, null);
        LoginUser user = SecurityUtils.getLoginUser();
        FlowParams flowParams = FlowParams.build()
                .handler(String.valueOf(user.getUserId()))
                .addHandlers(List.of(USER_PREFIX + delegateUserId));
        FlowEngine.taskService().transfer(taskId, flowParams);
        workflow.setAssigneeUserId(target.getId());
        workflow.setAssigneeName(target.getUsername());
        workflowMapper.updateById(workflow);
        saveLog(id, "DELEGATED", "转办给 " + target.getUsername());
        messageService.sendTodoToUsers(
                List.of(target.getId()),
                workflow.getTenantId(),
                "流程转办待办",
                "流程「" + workflow.getProcessName() + "」已转办给您，请及时处理。",
                MessageBizType.WORKFLOW,
                workflow.getId());
    }

    public List<SysWorkflowLogDO> logs(Long id) {
        return workflowLogMapper.selectList(new LambdaQueryWrapper<SysWorkflowLogDO>()
                .eq(SysWorkflowLogDO::getWorkflowId, id)
                .orderByAsc(SysWorkflowLogDO::getId));
    }

    public List<WarmFlowProcessNodeVO> currentNodes(Long id) {
        SysWorkflowDO workflow = getOrThrow(id);
        List<Task> tasks = FlowEngine.taskService().getByInsId(workflow.getFlowInstanceId());
        List<WarmFlowProcessNodeVO> result = new ArrayList<>();
        for (Task task : tasks) {
            WarmFlowProcessNodeVO vo = new WarmFlowProcessNodeVO();
            vo.setId(task.getId());
            vo.setTaskId(task.getId());
            vo.setNodeName(task.getNodeName());
            vo.setNodeKey(task.getNodeCode());
            vo.setNodeType(NodeType.BETWEEN.getKey().equals(task.getNodeType()) ? "APPROVE" : "CONDITION");
            result.add(vo);
        }
        return result;
    }

    public WorkflowDetailVo detail(Long id) {
        SysWorkflowDO workflow = getOrThrow(id);
        return WorkflowDetailVo.builder()
                .id(workflow.getId())
                .processName(workflow.getProcessName())
                .bizType(workflow.getBizType())
                .bizId(workflow.getBizId())
                .status(workflow.getStatus())
                .applicantId(workflow.getApplicantId())
                .applicantName(workflow.getApplicantName())
                .currentNodeName(workflow.getCurrentNodeName())
                .content(workflow.getContent())
                .remark(workflow.getRemark())
                .createdAt(workflow.getCreatedAt())
                .flowInstanceId(workflow.getFlowInstanceId())
                .formData(parseForm(workflow.getFormData()))
                .trace(buildTrace(workflow.getFlowInstanceId()))
                .currentNodes(currentNodes(id))
                .build();
    }

    private List<WorkflowTraceItemVo> buildTrace(Long flowInstanceId) {
        if (flowInstanceId == null) {
            return List.of();
        }
        List<HisTask> hisTasks = FlowEngine.hisTaskService().getByInsId(flowInstanceId);
        if (hisTasks == null || hisTasks.isEmpty()) {
            return List.of();
        }
        return hisTasks.stream()
                .sorted(Comparator.comparing(HisTask::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(task -> WorkflowTraceItemVo.builder()
                        .nodeCode(task.getNodeCode())
                        .nodeName(task.getNodeName())
                        .approver(task.getApprover())
                        .flowStatus(mapFlowStatus(task.getFlowStatus()))
                        .message(task.getMessage())
                        .createTime(toLocalDateTime(task.getCreateTime()))
                        .build())
                .toList();
    }

    private LocalDateTime toLocalDateTime(java.util.Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private void afterAction(SysWorkflowDO workflow, Instance instance, String action, String remark) {
        workflow.setStatus(mapFlowStatus(instance.getFlowStatus()));
        workflow.setCurrentNodeName(instance.getNodeName());
        workflowMapper.updateById(workflow);
        saveLog(workflow.getId(), action, remark);
        if (WorkflowStatus.APPROVED.name().equals(workflow.getStatus())) {
            notifyUser(workflow.getApplicantId(), workflow.getTenantId(), "流程审批通过",
                    "流程「" + workflow.getProcessName() + "」已全部审批通过。", workflow.getId());
        } else if (WorkflowStatus.REJECTED.name().equals(workflow.getStatus())) {
            notifyUser(workflow.getApplicantId(), workflow.getTenantId(), "流程审批拒绝",
                    "流程「" + workflow.getProcessName() + "」已被拒绝。", workflow.getId());
        } else {
            notifyTaskUsers(instance.getId(), workflow.getTenantId(), "流程待办",
                    "您有一条流程待办：「" + workflow.getProcessName() + "」等待审批。", workflow.getId());
        }
    }

    private Long resolveTaskId(SysWorkflowDO workflow, Long taskId, Long nodeId) {
        if (taskId != null) {
            return taskId;
        }
        if (nodeId != null) {
            return nodeId;
        }
        List<Task> tasks = FlowEngine.taskService().getByInsId(workflow.getFlowInstanceId());
        if (tasks.size() == 1) {
            return tasks.get(0).getId();
        }
        throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请指定要处理的待办任务");
    }

    private void collectTaskIds(String processedBy, Set<Long> taskIds) {
        List<User> users = FlowEngine.userService().list(FlowEngine.newUser().setProcessedBy(processedBy));
        users.stream()
                .map(User::getAssociated)
                .filter(associated -> associated != null)
                .forEach(taskIds::add);
    }

    private void notifyTaskUsers(Long instanceId, Long tenantId, String title, String content, Long workflowId) {
        List<Task> tasks = FlowEngine.taskService().getByInsId(instanceId);
        Set<Long> userIds = new HashSet<>();
        boolean broadcastToTenant = false;
        for (Task task : tasks) {
            List<User> users = FlowEngine.userService().listByAssociatedAndTypes(task.getId(),
                    UserType.APPROVAL.getKey(), UserType.TRANSFER.getKey(), UserType.DEPUTE.getKey());
            for (User user : users) {
                if (ALL_PERMISSION.equals(user.getProcessedBy())) {
                    broadcastToTenant = true;
                } else if (user.getProcessedBy() != null && user.getProcessedBy().startsWith(USER_PREFIX)) {
                    try {
                        userIds.add(Long.valueOf(user.getProcessedBy().substring(USER_PREFIX.length())));
                    } catch (NumberFormatException ignored) {
                        // ignore invalid user marker
                    }
                }
            }
        }
        if (broadcastToTenant) {
            userIds.addAll(userMapper.selectList(new LambdaQueryWrapper<SysUserDO>()
                            .eq(SysUserDO::getTenantId, tenantId)
                            .eq(SysUserDO::getStatus, 1))
                    .stream().map(SysUserDO::getId).toList());
        }
        messageService.sendTodoToUsers(new ArrayList<>(userIds), tenantId, title, content, MessageBizType.WORKFLOW, workflowId);
    }

    private void notifyUser(Long userId, Long tenantId, String title, String content, Long workflowId) {
        if (userId == null) {
            return;
        }
        messageService.sendSystemToUsers(List.of(userId), tenantId, title, content, MessageBizType.WORKFLOW, workflowId);
    }

    private SysWorkflowDO toWorkflowVO(Instance instance) {
        if (instance == null) {
            return null;
        }
        SysWorkflowDO workflow = null;
        try {
            workflow = workflowMapper.selectById(Long.valueOf(instance.getBusinessId()));
        } catch (NumberFormatException ignored) {
            // fallback to instance data
        }
        if (workflow == null) {
            workflow = new SysWorkflowDO();
        }
        workflow.setId(workflow.getId() == null ? instance.getId() : workflow.getId());
        workflow.setFlowInstanceId(instance.getId());
        workflow.setFlowDefId(instance.getDefinitionId());
        workflow.setProcessDefId(instance.getDefinitionId());
        workflow.setProcessName(StringUtils.hasText(workflow.getProcessName())
                ? workflow.getProcessName() : instance.getFlowName());
        workflow.setCurrentNodeName(instance.getNodeName());
        workflow.setStatus(mapFlowStatus(instance.getFlowStatus()));
        if (instance.getCreateBy() != null) {
            try {
                Long applicantId = Long.valueOf(instance.getCreateBy());
                workflow.setApplicantId(applicantId);
                SysUserDO applicant = userMapper.selectById(applicantId);
                if (applicant != null) {
                    workflow.setApplicantName(applicant.getUsername());
                }
            } catch (NumberFormatException ignored) {
                // keep existing applicant
            }
        }
        if (instance.getCreateTime() != null) {
            workflow.setCreatedAt(LocalDateTime.ofInstant(instance.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }
        return workflow;
    }

    private Map<String, Object> parseForm(String formData) {
        if (!StringUtils.hasText(formData)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(formData, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException exception) {
            return new HashMap<>();
        }
    }

    private SysWorkflowDO getOrThrow(Long id) {
        SysWorkflowDO workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return workflow;
    }

    private void saveLog(Long workflowId, String action, String remark) {
        LoginUser user = SecurityUtils.getLoginUser();
        SysWorkflowLogDO log = new SysWorkflowLogDO();
        log.setTenantId(TenantContext.getTenantId());
        log.setWorkflowId(workflowId);
        log.setAction(action);
        log.setOperatorId(user.getUserId());
        log.setOperatorName(user.getUsername());
        log.setRemark(remark);
        workflowLogMapper.insert(log);
    }

    private boolean isAdmin(LoginUser user) {
        return user.getRoles() != null && user.getRoles().contains("admin");
    }

    private String toWarmStatus(String status) {
        if (WorkflowStatus.PENDING.name().equals(status)) {
            return FlowStatus.APPROVAL.getKey();
        }
        if (WorkflowStatus.APPROVED.name().equals(status)) {
            return FlowStatus.FINISHED.getKey();
        }
        if (WorkflowStatus.REJECTED.name().equals(status)) {
            return FlowStatus.REJECT.getKey();
        }
        if (WorkflowStatus.WITHDRAWN.name().equals(status)) {
            return FlowStatus.CANCEL.getKey();
        }
        return status;
    }

    private String mapFlowStatus(String warmStatus) {
        if (warmStatus == null) {
            return WorkflowStatus.PENDING.name();
        }
        return switch (warmStatus) {
            case "2", "3", "8" -> WorkflowStatus.APPROVED.name();
            case "9" -> WorkflowStatus.REJECTED.name();
            case "4", "5", "6", "10" -> WorkflowStatus.WITHDRAWN.name();
            default -> WorkflowStatus.PENDING.name();
        };
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }
}
