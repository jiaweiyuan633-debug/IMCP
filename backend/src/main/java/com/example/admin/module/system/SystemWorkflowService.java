package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysWorkflow;
import com.example.admin.module.system.entity.SysProcessDef;
import com.example.admin.module.system.entity.SysProcessNode;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import com.example.admin.module.system.mapper.SysWorkflowLogMapper;
import com.example.admin.module.system.mapper.SysProcessDefMapper;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.entity.SysUser;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.entity.SysWorkflowLog;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import com.example.admin.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class SystemWorkflowService {

    private final SysWorkflowMapper workflowMapper;
    private final SysWorkflowLogMapper workflowLogMapper;
    private final SysProcessDefMapper processDefMapper;
    private final SysProcessNodeMapper processNodeMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;

    public PageResult<SysWorkflow> page(long pageNum, long pageSize, String status) {
        Page<SysWorkflow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysWorkflow> wrapper = new LambdaQueryWrapper<SysWorkflow>()
                .eq(status != null && !status.isBlank(), SysWorkflow::getStatus, status)
                .orderByDesc(SysWorkflow::getId);
        IPage<SysWorkflow> result = workflowMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public Long create(SysWorkflow workflow) {
        if (workflow.getProcessDefId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "请选择流程定义");
        }
        SysProcessDef def = processDefMapper.selectById(workflow.getProcessDefId());
        if (def == null || def.getStatus() == null || def.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流程定义不可用");
        }
        List<SysProcessNode> nodes = processNodeMapper.selectList(new LambdaQueryWrapper<SysProcessNode>()
                .eq(SysProcessNode::getProcessDefId, def.getId())
                .orderByAsc(SysProcessNode::getNodeOrder));
        if (nodes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流程定义没有审批节点");
        }
        LoginUser user = SecurityUtils.getLoginUser();
        workflow.setId(null);
        workflow.setProcessDefId(def.getId());
        workflow.setCurrentNodeId(nodes.get(0).getId());
        workflow.setCurrentNodeName(nodes.get(0).getNodeName());
        workflow.setApplicantId(user.getUserId());
        workflow.setApplicantName(user.getUsername());
        workflow.setStatus("PENDING");
        workflow.setTenantId(TenantContext.getTenantId());
        workflowMapper.insert(workflow);
        saveLog(workflow.getId(), "STARTED", "发起流程：" + def.getDefName());
        return workflow.getId();
    }

    public PageResult<SysWorkflow> taskPage(long pageNum, long pageSize) {
        Page<SysWorkflow> page = new Page<>(pageNum, pageSize);
        LoginUser user = SecurityUtils.getLoginUser();
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            IPage<SysWorkflow> result = workflowMapper.selectPage(page, new LambdaQueryWrapper<SysWorkflow>()
                    .eq(SysWorkflow::getStatus, "PENDING")
                    .orderByDesc(SysWorkflow::getId));
            return PageResult.of(result, result.getRecords());
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getUserId());
        if (roleIds.isEmpty()) {
            return PageResult.of(page, Collections.emptyList());
        }
        List<SysProcessNode> nodes = processNodeMapper.selectList(new LambdaQueryWrapper<SysProcessNode>()
                .eq(SysProcessNode::getTenantId, TenantContext.getTenantId())
                .and(wrapper -> wrapper.in(SysProcessNode::getApproverRoleId, roleIds)
                        .or().isNull(SysProcessNode::getApproverRoleId)));
        List<Long> nodeIds = nodes.stream().map(SysProcessNode::getId).toList();
        if (nodeIds.isEmpty()) {
            return PageResult.of(page, Collections.emptyList());
        }
        IPage<SysWorkflow> result = workflowMapper.selectPage(page, new LambdaQueryWrapper<SysWorkflow>()
                .eq(SysWorkflow::getStatus, "PENDING")
                .and(wrapper -> wrapper.in(SysWorkflow::getCurrentNodeId, nodeIds)
                        .or().eq(SysWorkflow::getAssigneeUserId, user.getUserId()))
                .orderByDesc(SysWorkflow::getId));
        return PageResult.of(result, result.getRecords());
    }

    @Transactional
    public void approve(Long id, String remark) {
        SysWorkflow workflow = getOrThrow(id);
        if (!"PENDING".equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "当前流程已结束");
        }
        SysProcessNode next = nextNode(workflow);
        if (next == null) {
            workflow.setStatus("APPROVED");
            workflow.setCurrentNodeId(null);
            workflow.setCurrentNodeName(null);
        } else {
            workflow.setCurrentNodeId(next.getId());
            workflow.setCurrentNodeName(next.getNodeName());
        }
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
        saveLog(id, "APPROVED", remark == null ? "审批通过" : remark);
    }

    @Transactional
    public void reject(Long id, String remark) {
        SysWorkflow workflow = getOrThrow(id);
        if (!"PENDING".equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "当前流程已结束");
        }
        workflow.setStatus("REJECTED");
        workflow.setCurrentNodeId(null);
        workflow.setCurrentNodeName(null);
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
        saveLog(id, "REJECTED", remark == null ? "审批拒绝" : remark);
    }

    @Transactional
    public void withdraw(Long id, String remark) {
        SysWorkflow workflow = getOrThrow(id);
        LoginUser user = SecurityUtils.getLoginUser();
        boolean isAdmin = user.getRoles() != null && user.getRoles().contains("admin");
        if (!isAdmin && !user.getUserId().equals(workflow.getApplicantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (!"PENDING".equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "当前流程已结束");
        }
        workflow.setStatus("WITHDRAWN");
        workflow.setCurrentNodeId(null);
        workflow.setCurrentNodeName(null);
        workflow.setAssigneeUserId(null);
        workflow.setAssigneeName(null);
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
        saveLog(id, "WITHDRAWN", remark == null ? "发起人撤回" : remark);
    }

    @Transactional
    public void delegate(Long id, Long delegateUserId) {
        SysWorkflow workflow = getOrThrow(id);
        if (!"PENDING".equals(workflow.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "当前流程已结束");
        }
        SysUser target = userMapper.selectById(delegateUserId);
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        workflow.setAssigneeUserId(target.getId());
        workflow.setAssigneeName(target.getUsername());
        workflowMapper.updateById(workflow);
        saveLog(id, "DELEGATED", "转办给 " + target.getUsername());
    }

    public List<SysWorkflowLog> logs(Long id) {
        return workflowLogMapper.selectList(new LambdaQueryWrapper<SysWorkflowLog>()
                .eq(SysWorkflowLog::getWorkflowId, id)
                .orderByAsc(SysWorkflowLog::getId));
    }

    private SysWorkflow getOrThrow(Long id) {
        SysWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return workflow;
    }

    private SysProcessNode nextNode(SysWorkflow workflow) {
        if (workflow.getProcessDefId() == null || workflow.getCurrentNodeId() == null) {
            return null;
        }
        List<SysProcessNode> nodes = processNodeMapper.selectList(new LambdaQueryWrapper<SysProcessNode>()
                .eq(SysProcessNode::getProcessDefId, workflow.getProcessDefId())
                .orderByAsc(SysProcessNode::getNodeOrder));
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId().equals(workflow.getCurrentNodeId())) {
                return i + 1 < nodes.size() ? nodes.get(i + 1) : null;
            }
        }
        return null;
    }

    private void saveLog(Long workflowId, String action, String remark) {
        LoginUser user = SecurityUtils.getLoginUser();
        SysWorkflowLog log = new SysWorkflowLog();
        log.setTenantId(TenantContext.getTenantId());
        log.setWorkflowId(workflowId);
        log.setAction(action);
        log.setOperatorId(user.getUserId());
        log.setOperatorName(user.getUsername());
        log.setRemark(remark);
        workflowLogMapper.insert(log);
    }
}

