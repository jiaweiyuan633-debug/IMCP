package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.DeptSaveRequest;
import com.example.admin.module.system.entity.SysDept;
import com.example.admin.module.system.mapper.SysDeptMapper;
import com.example.admin.module.system.vo.DeptVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemDeptService {

    private final SysDeptMapper deptMapper;

    public List<DeptVo> tree() {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getOrderNum)
                .orderByAsc(SysDept::getId));
        return buildTree(depts, 0L);
    }

    public Long create(DeptSaveRequest request) {
        SysDept dept = toEntity(request);
        dept.setAncestors(buildAncestors(request.getParentId()));
        deptMapper.insert(dept);
        return dept.getId();
    }

    public void update(DeptSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "部门 ID 不能为空");
        }
        SysDept dept = deptMapper.selectById(request.getId());
        if (dept == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        dept.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        dept.setAncestors(buildAncestors(dept.getParentId()));
        dept.setDeptName(request.getDeptName());
        dept.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        dept.setLeader(request.getLeader());
        dept.setPhone(request.getPhone());
        dept.setEmail(request.getEmail());
        dept.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        deptMapper.updateById(dept);
    }

    public void delete(Long id) {
        Long children = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (children > 0) {
            throw new BusinessException(1008, "存在下级部门，不能删除");
        }
        deptMapper.deleteById(id);
    }

    private SysDept toEntity(DeptSaveRequest request) {
        SysDept dept = new SysDept();
        dept.setId(request.getId());
        dept.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        dept.setDeptName(request.getDeptName());
        dept.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        dept.setLeader(request.getLeader());
        dept.setPhone(request.getPhone());
        dept.setEmail(request.getEmail());
        dept.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        return dept;
    }

    private String buildAncestors(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "0";
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            return "0";
        }
        return parent.getAncestors() + "," + parent.getId();
    }

    private List<DeptVo> buildTree(List<SysDept> depts, Long parentId) {
        List<DeptVo> result = new ArrayList<>();
        for (SysDept dept : depts) {
            if (parentId.equals(dept.getParentId())) {
                result.add(toVo(dept, buildTree(depts, dept.getId())));
            }
        }
        return result;
    }

    private DeptVo toVo(SysDept dept, List<DeptVo> children) {
        return DeptVo.builder()
                .id(dept.getId())
                .parentId(dept.getParentId())
                .deptName(dept.getDeptName())
                .orderNum(dept.getOrderNum())
                .leader(dept.getLeader())
                .phone(dept.getPhone())
                .email(dept.getEmail())
                .status(dept.getStatus())
                .children(children)
                .build();
    }
}

