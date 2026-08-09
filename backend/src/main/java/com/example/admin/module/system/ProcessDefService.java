package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.dto.ProcessDefSaveRequest;
import com.example.admin.module.system.entity.SysProcessDef;
import com.example.admin.module.system.entity.SysProcessNode;
import com.example.admin.module.system.entity.SysWorkflow;
import com.example.admin.module.system.mapper.SysProcessDefMapper;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessDefService {

    private final SysProcessDefMapper defMapper;
    private final SysProcessNodeMapper nodeMapper;
    private final SysWorkflowMapper workflowMapper;

    public PageResult<SysProcessDef> page(long pageNum, long pageSize, String defName, Integer status) {
        Page<SysProcessDef> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysProcessDef> wrapper = new LambdaQueryWrapper<SysProcessDef>()
                .like(StringUtils.hasText(defName), SysProcessDef::getDefName, defName)
                .eq(status != null, SysProcessDef::getStatus, status)
                .orderByDesc(SysProcessDef::getId);
        IPage<SysProcessDef> result = defMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public List<SysProcessDef> listOptions() {
        return defMapper.selectList(new LambdaQueryWrapper<SysProcessDef>()
                .eq(SysProcessDef::getStatus, 1)
                .orderByDesc(SysProcessDef::getId));
    }

    public List<SysProcessNode> nodes(Long defId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<SysProcessNode>()
                .eq(SysProcessNode::getProcessDefId, defId)
                .orderByAsc(SysProcessNode::getNodeOrder));
    }

    @Transactional
    public Long create(ProcessDefSaveRequest request) {
        SysProcessDef def = new SysProcessDef();
        def.setTenantId(TenantContext.getTenantId());
        def.setDefName(request.getDefName());
        def.setDefKey(request.getDefKey());
        def.setDescription(request.getDescription());
        def.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        def.setCreatedBy(tryGetUserId());
        defMapper.insert(def);
        saveNodes(def.getId(), request.getNodes());
        return def.getId();
    }

    @Transactional
    public void update(ProcessDefSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流程定义 ID 不能为空");
        }
        SysProcessDef def = defMapper.selectById(request.getId());
        if (def == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        ensureNoActiveInstances(def.getId());
        def.setDefName(request.getDefName());
        def.setDefKey(request.getDefKey());
        def.setDescription(request.getDescription());
        def.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        def.setUpdatedBy(tryGetUserId());
        defMapper.updateById(def);
        nodeMapper.delete(new LambdaQueryWrapper<SysProcessNode>()
                .eq(SysProcessNode::getProcessDefId, def.getId()));
        saveNodes(def.getId(), request.getNodes());
    }

    @Transactional
    public void delete(Long id) {
        ensureNoActiveInstances(id);
        nodeMapper.delete(new LambdaQueryWrapper<SysProcessNode>()
                .eq(SysProcessNode::getProcessDefId, id));
        defMapper.deleteById(id);
    }

    private void saveNodes(Long defId, List<ProcessDefSaveRequest.NodeItem> nodes) {
        int order = 0;
        for (ProcessDefSaveRequest.NodeItem item : nodes) {
            SysProcessNode node = new SysProcessNode();
            node.setTenantId(TenantContext.getTenantId());
            node.setProcessDefId(defId);
            node.setNodeName(item.getNodeName());
            node.setNodeKey(item.getNodeKey());
            node.setNodeType(item.getNodeType() == null ? "APPROVE" : item.getNodeType());
            node.setConditionExpression(item.getConditionExpression());
            node.setTimeoutHours(item.getTimeoutHours() == null ? 48 : item.getTimeoutHours());
            node.setNodeOrder(item.getNodeOrder() == null ? order : item.getNodeOrder());
            node.setApproverRoleId(item.getApproverRoleId());
            nodeMapper.insert(node);
            order++;
        }
    }

    private void ensureNoActiveInstances(Long defId) {
        long active = workflowMapper.selectCount(new LambdaQueryWrapper<SysWorkflow>()
                .eq(SysWorkflow::getProcessDefId, defId)
                .eq(SysWorkflow::getStatus, WorkflowStatus.PENDING.name()));
        if (active > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "存在运行中的流程实例，不能修改或删除流程定义");
        }
    }

    private Long tryGetUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception exception) {
            return null;
        }
    }
}
