package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.dto.ProcessDefSaveRequest;
import com.example.admin.module.system.entity.SysProcessDefDO;
import com.example.admin.module.system.entity.SysProcessNodeDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
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

    private static final int ENABLED = 1;
    private static final String DEFAULT_NODE_TYPE = "APPROVE";
    private static final int DEFAULT_TIMEOUT_HOURS = 48;

    private final SysProcessDefMapper defMapper;
    private final SysProcessNodeMapper nodeMapper;
    private final SysWorkflowMapper workflowMapper;

    public PageResult<SysProcessDefDO> page(long pageNum, long pageSize, String defName, Integer status) {
        Page<SysProcessDefDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysProcessDefDO> wrapper = new LambdaQueryWrapper<SysProcessDefDO>()
                .like(StringUtils.hasText(defName), SysProcessDefDO::getDefName, defName)
                .eq(status != null, SysProcessDefDO::getStatus, status)
                .orderByDesc(SysProcessDefDO::getId);
        IPage<SysProcessDefDO> result = defMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public List<SysProcessDefDO> listOptions() {
        return defMapper.selectList(new LambdaQueryWrapper<SysProcessDefDO>()
                .eq(SysProcessDefDO::getStatus, ENABLED)
                .orderByDesc(SysProcessDefDO::getId));
    }

    public List<SysProcessNodeDO> nodes(Long defId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<SysProcessNodeDO>()
                .eq(SysProcessNodeDO::getProcessDefId, defId)
                .orderByAsc(SysProcessNodeDO::getNodeOrder));
    }

    @Transactional
    public Long create(ProcessDefSaveRequest request) {
        SysProcessDefDO def = new SysProcessDefDO();
        def.setTenantId(TenantContext.getTenantId());
        def.setDefName(request.getDefName());
        def.setDefKey(request.getDefKey());
        def.setDescription(request.getDescription());
        def.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        def.setCreatedBy(SecurityUtils.tryGetUserId());
        defMapper.insert(def);
        saveNodes(def.getId(), request.getNodes());
        return def.getId();
    }

    @Transactional
    public void update(ProcessDefSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流程定义 ID 不能为空");
        }
        SysProcessDefDO def = defMapper.selectById(request.getId());
        if (def == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        ensureNoActiveInstances(def.getId());
        def.setDefName(request.getDefName());
        def.setDefKey(request.getDefKey());
        def.setDescription(request.getDescription());
        def.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        def.setUpdatedBy(SecurityUtils.tryGetUserId());
        defMapper.updateById(def);
        nodeMapper.delete(new LambdaQueryWrapper<SysProcessNodeDO>()
                .eq(SysProcessNodeDO::getProcessDefId, def.getId()));
        saveNodes(def.getId(), request.getNodes());
    }

    @Transactional
    public void delete(Long id) {
        ensureNoActiveInstances(id);
        nodeMapper.delete(new LambdaQueryWrapper<SysProcessNodeDO>()
                .eq(SysProcessNodeDO::getProcessDefId, id));
        defMapper.deleteById(id);
    }

    private void saveNodes(Long defId, List<ProcessDefSaveRequest.NodeItem> nodes) {
        int order = 0;
        for (ProcessDefSaveRequest.NodeItem item : nodes) {
            SysProcessNodeDO node = new SysProcessNodeDO();
            node.setTenantId(TenantContext.getTenantId());
            node.setProcessDefId(defId);
            node.setNodeName(item.getNodeName());
            node.setNodeKey(item.getNodeKey());
            node.setNodeType(item.getNodeType() == null ? DEFAULT_NODE_TYPE : item.getNodeType());
            node.setConditionExpression(item.getConditionExpression());
            node.setTimeoutHours(item.getTimeoutHours() == null
                    ? DEFAULT_TIMEOUT_HOURS
                    : item.getTimeoutHours());
            node.setNodeOrder(item.getNodeOrder() == null ? order : item.getNodeOrder());
            node.setApproverRoleId(item.getApproverRoleId());
            nodeMapper.insert(node);
            order++;
        }
    }

    private void ensureNoActiveInstances(Long defId) {
        long active = workflowMapper.selectCount(new LambdaQueryWrapper<SysWorkflowDO>()
                .eq(SysWorkflowDO::getProcessDefId, defId)
                .eq(SysWorkflowDO::getStatus, WorkflowStatus.PENDING.name()));
        if (active > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "存在运行中的流程实例，不能修改或删除流程定义");
        }
    }

}
