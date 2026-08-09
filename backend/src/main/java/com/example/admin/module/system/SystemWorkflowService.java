package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysWorkflow;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemWorkflowService {

    private final SysWorkflowMapper workflowMapper;

    public PageResult<SysWorkflow> page(long pageNum, long pageSize, String status) {
        Page<SysWorkflow> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysWorkflow> wrapper = new LambdaQueryWrapper<SysWorkflow>()
                .eq(status != null && !status.isBlank(), SysWorkflow::getStatus, status)
                .orderByDesc(SysWorkflow::getId);
        IPage<SysWorkflow> result = workflowMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public Long create(SysWorkflow workflow) {
        LoginUser user = SecurityUtils.getLoginUser();
        workflow.setId(null);
        workflow.setApplicantId(user.getUserId());
        workflow.setApplicantName(user.getUsername());
        workflow.setStatus("PENDING");
        workflowMapper.insert(workflow);
        return workflow.getId();
    }

    public void approve(Long id, String remark) {
        SysWorkflow workflow = getOrThrow(id);
        workflow.setStatus("APPROVED");
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
    }

    public void reject(Long id, String remark) {
        SysWorkflow workflow = getOrThrow(id);
        workflow.setStatus("REJECTED");
        workflow.setRemark(remark);
        workflowMapper.updateById(workflow);
    }

    private SysWorkflow getOrThrow(Long id) {
        SysWorkflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return workflow;
    }
}

