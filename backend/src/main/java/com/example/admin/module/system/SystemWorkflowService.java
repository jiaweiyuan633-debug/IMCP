package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysProcessDefDO;
import com.example.admin.module.system.entity.SysProcessNodeDO;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
import com.example.admin.module.system.entity.SysWorkflowLogDO;
import com.example.admin.module.system.entity.SysNoticeDO;
import com.example.admin.module.system.mapper.SysProcessDefMapper;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.mapper.SysWorkflowLogMapper;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemWorkflowService {

    private static final int DEFAULT_TIMEOUT_HOURS = 48;
    private static final int TIMEOUT_NOTIFIED = 1;
    private static final int TIMEOUT_NOT_NOTIFIED = 0;
    private static final int NOTICE_TYPE = 1;
    private static final int NOTICE_STATUS = 1;

    private final SysWorkflowMapper workflowMapper;
    private final SysWorkflowLogMapper workflowLogMapper;
    private final SysProcessDefMapper processDefMapper;
    private final SysProcessNodeMapper processNodeMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SystemNoticeService noticeService;
    private final ObjectMapper objectMapper;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    public PageResult<SysWorkflowDO> page(long pageNum, long pageSize, String status) {
        Page<SysWorkflowDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysWorkflowDO> wrapper = new LambdaQueryWrapper<SysWorkflowDO>()
                .eq(status != null && !status.isBlank(), SysWorkflowDO::getStatus, status)
                .orderByDesc(SysWorkflowDO::getId);
        IPage<SysWorkflowDO> result = workflowMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    @Transactional
    public Long create(SysWorkflowDO workflow) {
        if (workflow.getProcessDefId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择流程定义");
        }
        SysProcessDefDO def = processDefMapper.selectById(workflow.getProcessDefId());
        if (def == null || def.getStatus() == null || def.getStatus() != 1) {
            throw new BusinessException(ResultCode.WORKFLOW_DEF_INVALID);
        }
        List<SysProcessNodeDO> nodes = nodesOfDef(def.getId());
        Map<String, Object> form = parseForm(workflow.getFormData());
        int startOrder = nodes.stream()
                .map(SysProcessNodeDO::getNodeOrder)
                .filter(order -> order != null)
                .min(Integer::compareTo)
                .orElse(0);
        List<SysProcessNodeDO> startNodes = selectNodes(nodes, startOrder, form);
        if (startNodes.isEmpty()) {
            throw new BusinessException(ResultCode.WORKFLOW_NO_START_NODE);
        }
        LoginUser user = SecurityUtils.getLoginUser();
        workflow.setId(null);
        workflow.setProcessDefId(def.getId());
        applyCurrentNodes(workflow, startNodes);
        workflow.setApplicantId(user.getUserId());
        workflow.setApplicantName(user.getUsername());
        workflow.setStatus(WorkflowStatus.PENDING.name());
        workflow.setTenantId(TenantContext.getTenantId());
        workflow.setCurrentNodeAssignedAt(LocalDateTime.now());
        workflow.setTimeoutNotified(TIMEOUT_NOT_NOTIFIED);
        workflowMapper.insert(workflow);
        saveLog(workflow.getId(), "STARTED", "发起流程：" + def.getDefName());
        return workflow.getId();
    }

    public PageResult<SysWorkflowDO> taskPage(long pageNum, long pageSize) {
        Page<SysWorkflowDO> page = new Page<>(pageNum, pageSize);
        LoginUser user = SecurityUtils.getLoginUser();
        LambdaQueryWrapper<SysWorkflowDO> wrapper = new LambdaQueryWrapper<SysWorkflowDO>()
                .eq(SysWorkflowDO::getStatus, WorkflowStatus.PENDING.name());
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            wrapper.orderByDesc(SysWorkflowDO::getId);
        } else {
            List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getUserId());
            List<SysProcessNodeDO> nodes = roleIds.isEmpty() ? Collections.emptyList()
                    : processNodeMapper.selectList(new LambdaQueryWrapper<SysProcessNodeDO>()
                            .eq(SysProcessNodeDO::getTenantId, TenantContext.getTenantId())
                            .and(q -> q.in(SysProcessNodeDO::getApproverRoleId, roleIds)
                                    .or().isNull(SysProcessNodeDO::getApproverRoleId)));
            wrapper.and(q -> {
                for (SysProcessNodeDO node : nodes) {
                    q.or().apply("FIND_IN_SET({0}, current_node_ids) > 0", node.getId());
                    q.or().in(SysWorkflowDO::getCurrentNodeId, node.getId());
                }
                q.or().eq(SysWorkflowDO::getAssigneeUserId, user.getUserId());
            });
            wrapper.orderByDesc(SysWorkflowDO::getId);
        }
        IPage<SysWorkflowDO> result = workflowMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public List<SysProcessNodeDO> currentNodes(Long id) {
        SysWorkflowDO workflow = getOrThrow(id);
        List<Long> ids = parseIds(workflow.getCurrentNodeIds());
        if (ids.isEmpty() && workflow.getCurrentNodeId() != null) {
            ids = List.of(workflow.getCurrentNodeId());
        }
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(processNodeMapper::selectById)
                .filter(node -> node != null)
                .sorted(Comparator.comparing(SysProcessNodeDO::getNodeOrder))
                .toList();
    }

    @Transactional
    public void approve(Long id, Long nodeId, String remark) {
        SysWorkflowDO workflow = getOrThrow(id);
        if (!WorkflowStatus.PENDING.name().equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.WORKFLOW_FINISHED);
        }
        List<Long> currentIds = parseIds(workflow.getCurrentNodeIds());
        if (currentIds.isEmpty() && workflow.getCurrentNodeId() != null) {
            currentIds = new ArrayList<>(List.of(workflow.getCurrentNodeId()));
        }
        if (nodeId == null) {
            if (currentIds.size() != 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择要审批的节点");
            }
            nodeId = currentIds.get(0);
        }
        if (!currentIds.contains(nodeId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "节点不在当前待办中");
        }
        SysProcessNodeDO node = processNodeMapper.selectById(nodeId);
        checkApprover(workflow, node);
        currentIds.remove(nodeId);
        saveLog(id, "APPROVED", (remark == null ? "审批通过" : remark) + "，节点：" + node.getNodeName());
        if (!currentIds.isEmpty()) {
            workflow.setCurrentNodeIds(joinIds(currentIds));
            updateCurrentDisplay(workflow, currentIds);
            workflowMapper.updateById(workflow);
            return;
        }
        List<SysProcessNodeDO> allNodes = nodesOfDef(workflow.getProcessDefId());
        int currentOrder = node.getNodeOrder();
        List<SysProcessNodeDO> next = nextNodes(allNodes, currentOrder, parseForm(workflow.getFormData()));
        if (next.isEmpty()) {
            finishWorkflow(workflow, WorkflowStatus.APPROVED.name());
            workflowMapper.updateById(workflow);
            return;
        }
        applyCurrentNodes(workflow, next);
        workflow.setCurrentNodeAssignedAt(LocalDateTime.now());
        workflow.setTimeoutNotified(TIMEOUT_NOT_NOTIFIED);
        workflowMapper.updateById(workflow);
        saveLog(id, "ADVANCED", "进入下一审批环节");
    }

    @Transactional
    public void reject(Long id, String remark) {
        SysWorkflowDO workflow = getOrThrow(id);
        if (!WorkflowStatus.PENDING.name().equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.WORKFLOW_FINISHED);
        }
        checkCanOperate(workflow, currentIds(workflow));
        finishWorkflow(workflow, WorkflowStatus.REJECTED.name());
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
        saveLog(id, "REJECTED", remark == null ? "审批拒绝" : remark);
    }

    @Transactional
    public void withdraw(Long id, String remark) {
        SysWorkflowDO workflow = getOrThrow(id);
        LoginUser user = SecurityUtils.getLoginUser();
        boolean isAdmin = user.getRoles() != null && user.getRoles().contains("admin");
        if (!isAdmin && !user.getUserId().equals(workflow.getApplicantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!WorkflowStatus.PENDING.name().equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.WORKFLOW_FINISHED);
        }
        finishWorkflow(workflow, WorkflowStatus.WITHDRAWN.name());
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
        saveLog(id, "WITHDRAWN", remark == null ? "发起人撤回" : remark);
    }

    @Transactional
    public void delegate(Long id, Long delegateUserId) {
        SysWorkflowDO workflow = getOrThrow(id);
        if (!WorkflowStatus.PENDING.name().equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.WORKFLOW_FINISHED);
        }
        checkCanOperate(workflow, currentIds(workflow));
        SysUserDO target = userMapper.selectById(delegateUserId);
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        workflow.setAssigneeUserId(target.getId());
        workflow.setAssigneeName(target.getUsername());
        workflowMapper.updateById(workflow);
        saveLog(id, "DELEGATED", "转办给 " + target.getUsername());
    }

    public List<SysWorkflowLogDO> logs(Long id) {
        return workflowLogMapper.selectList(new LambdaQueryWrapper<SysWorkflowLogDO>()
                .eq(SysWorkflowLogDO::getWorkflowId, id)
                .orderByAsc(SysWorkflowLogDO::getId));
    }

    @Scheduled(
            initialDelayString = "${app.workflow-timeout-check-initial-delay-ms:60000}",
            fixedDelayString = "${app.workflow-timeout-check-ms:60000}")
    public void checkTimeoutReminders() {
        List<SysWorkflowDO> pending = workflowMapper.selectList(new LambdaQueryWrapper<SysWorkflowDO>()
                .eq(SysWorkflowDO::getStatus, WorkflowStatus.PENDING.name()));
        try {
            for (SysWorkflowDO workflow : pending) {
                try {
                    if (workflow.getCurrentNodeAssignedAt() == null
                            || workflow.getTimeoutNotified() != null
                            && workflow.getTimeoutNotified() == TIMEOUT_NOTIFIED) {
                        continue;
                    }
                    List<SysProcessNodeDO> nodes = currentNodes(workflow.getId());
                    int timeoutHours = nodes.stream()
                            .map(SysProcessNodeDO::getTimeoutHours)
                            .filter(hours -> hours != null)
                            .min(Integer::compareTo)
                            .orElse(DEFAULT_TIMEOUT_HOURS);
                    if (workflow.getCurrentNodeAssignedAt().plusHours(timeoutHours).isAfter(LocalDateTime.now())) {
                        continue;
                    }
                    TenantContext.setTenantId(workflow.getTenantId());
                    SysNoticeDO notice = new SysNoticeDO();
                    notice.setNoticeTitle("流程超时提醒");
                    notice.setNoticeType(NOTICE_TYPE);
                    notice.setNoticeContent("流程「" + workflow.getProcessName() + "」在节点等待超过 " + timeoutHours + " 小时。");
                    notice.setStatus(NOTICE_STATUS);
                    noticeService.create(notice);
                    workflow.setTimeoutNotified(TIMEOUT_NOTIFIED);
                    workflowMapper.updateById(workflow);
                } catch (RuntimeException exception) {
                    log.warn("Workflow timeout reminder failed for id={}", workflow.getId(), exception);
                }
            }
        } catch (RuntimeException exception) {
            log.error("Workflow timeout check failed", exception);
        } finally {
            TenantContext.clear();
        }
    }

    private SysWorkflowDO getOrThrow(Long id) {
        SysWorkflowDO workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return workflow;
    }

    private List<SysProcessNodeDO> nodesOfDef(Long defId) {
        return processNodeMapper.selectList(new LambdaQueryWrapper<SysProcessNodeDO>()
                .eq(SysProcessNodeDO::getProcessDefId, defId)
                .orderByAsc(SysProcessNodeDO::getNodeOrder)
                .orderByAsc(SysProcessNodeDO::getId));
    }

    private List<SysProcessNodeDO> nextNodes(List<SysProcessNodeDO> allNodes, int currentOrder, Map<String, Object> form) {
        int nextOrder = allNodes.stream()
                .map(SysProcessNodeDO::getNodeOrder)
                .filter(order -> order > currentOrder)
                .min(Integer::compareTo)
                .orElse(-1);
        if (nextOrder < 0) {
            return Collections.emptyList();
        }
        return selectNodes(allNodes, nextOrder, form);
    }

    private List<SysProcessNodeDO> selectNodes(List<SysProcessNodeDO> allNodes, int order, Map<String, Object> form) {
        return allNodes.stream()
                .filter(node -> node.getNodeOrder() != null && node.getNodeOrder() == order)
                .filter(node -> matchesCondition(node, form))
                .toList();
    }

    private boolean matchesCondition(SysProcessNodeDO node, Map<String, Object> form) {
        if (!StringUtils.hasText(node.getConditionExpression())) {
            return true;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariables(form);
            Boolean result = spelParser.parseExpression(node.getConditionExpression())
                    .getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (ExpressionException exception) {
            log.warn("Workflow condition evaluate failed: {}", node.getConditionExpression(), exception);
            return false;
        }
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

    private void applyCurrentNodes(SysWorkflowDO workflow, List<SysProcessNodeDO> nodes) {
        List<Long> ids = nodes.stream().map(SysProcessNodeDO::getId).toList();
        workflow.setCurrentNodeIds(joinIds(ids));
        updateCurrentDisplay(workflow, ids);
    }

    private void updateCurrentDisplay(SysWorkflowDO workflow, List<Long> ids) {
        List<SysProcessNodeDO> nodes = ids.stream()
                .map(processNodeMapper::selectById)
                .filter(node -> node != null)
                .sorted(Comparator.comparing(SysProcessNodeDO::getNodeOrder))
                .toList();
        workflow.setCurrentNodeId(nodes.isEmpty() ? null : nodes.get(0).getId());
        workflow.setCurrentNodeName(nodes.stream().map(SysProcessNodeDO::getNodeName).collect(Collectors.joining(", ")));
    }

    private List<Long> parseIds(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                ids.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip invalid id
            }
        }
        return ids;
    }

    private List<Long> currentIds(SysWorkflowDO workflow) {
        List<Long> ids = parseIds(workflow.getCurrentNodeIds());
        if (ids.isEmpty() && workflow.getCurrentNodeId() != null) {
            ids = new ArrayList<>(List.of(workflow.getCurrentNodeId()));
        }
        return ids;
    }

    private void checkApprover(SysWorkflowDO workflow, SysProcessNodeDO node) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            return;
        }
        if (workflow.getAssigneeUserId() != null) {
            if (!workflow.getAssigneeUserId().equals(user.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            return;
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getUserId());
        if (node.getApproverRoleId() == null || roleIds.contains(node.getApproverRoleId())) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    private void checkCanOperate(SysWorkflowDO workflow, List<Long> nodeIds) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            return;
        }
        if (workflow.getAssigneeUserId() != null) {
            if (!workflow.getAssigneeUserId().equals(user.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            return;
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getUserId());
        boolean allowed = nodeIds.stream()
                .map(processNodeMapper::selectById)
                .filter(node -> node != null)
                .anyMatch(node -> node.getApproverRoleId() == null || roleIds.contains(node.getApproverRoleId()));
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void finishWorkflow(SysWorkflowDO workflow, String status) {
        workflow.setStatus(status);
        workflow.setCurrentNodeId(null);
        workflow.setCurrentNodeName(null);
        workflow.setCurrentNodeIds(null);
        workflow.setAssigneeUserId(null);
        workflow.setAssigneeName(null);
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
}
