package cn.admin.scaffold.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.common.annotation.FieldAudit;
import cn.admin.scaffold.module.system.dto.DeptSaveRequest;
import cn.admin.scaffold.module.system.entity.SysDeptDO;
import cn.admin.scaffold.module.system.mapper.SysDeptMapper;
import cn.admin.scaffold.module.system.vo.DeptVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemDeptService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final int DEFAULT_ORDER = 0;
    private static final int ENABLED = 1;

    private final SysDeptMapper deptMapper;

    public List<DeptVo> tree() {
        List<SysDeptDO> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDeptDO>()
                .orderByAsc(SysDeptDO::getOrderNum)
                .orderByAsc(SysDeptDO::getId));
        return buildTree(depts, ROOT_PARENT_ID);
    }

    public Long create(DeptSaveRequest request) {
        SysDeptDO dept = toEntity(request);
        dept.setAncestors(buildAncestors(request.getParentId()));
        deptMapper.insert(dept);
        return dept.getId();
    }

    @FieldAudit(entity = SysDeptDO.class, action = "UPDATE", module = "部门管理")
    public void update(DeptSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "部门 ID 不能为空");
        }
        SysDeptDO dept = deptMapper.selectById(request.getId());
        if (dept == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        dept.setParentId(request.getParentId() == null ? Long.valueOf(ROOT_PARENT_ID) : request.getParentId());
        dept.setAncestors(buildAncestors(dept.getParentId()));
        dept.setDeptName(request.getDeptName());
        dept.setOrderNum(request.getOrderNum() == null ? Integer.valueOf(DEFAULT_ORDER) : request.getOrderNum());
        dept.setLeader(request.getLeader());
        dept.setPhone(request.getPhone());
        dept.setEmail(request.getEmail());
        dept.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        deptMapper.updateById(dept);
    }

    public void delete(Long id) {
        Long children = deptMapper.selectCount(new LambdaQueryWrapper<SysDeptDO>()
                .eq(SysDeptDO::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ResultCode.DEPT_HAS_CHILDREN);
        }
        deptMapper.deleteById(id);
    }

    private SysDeptDO toEntity(DeptSaveRequest request) {
        SysDeptDO dept = new SysDeptDO();
        dept.setId(request.getId());
        dept.setParentId(request.getParentId() == null ? Long.valueOf(ROOT_PARENT_ID) : request.getParentId());
        dept.setDeptName(request.getDeptName());
        dept.setOrderNum(request.getOrderNum() == null ? Integer.valueOf(DEFAULT_ORDER) : request.getOrderNum());
        dept.setLeader(request.getLeader());
        dept.setPhone(request.getPhone());
        dept.setEmail(request.getEmail());
        dept.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        return dept;
    }

    private String buildAncestors(Long parentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return "0";
        }
        SysDeptDO parent = deptMapper.selectById(parentId);
        if (parent == null) {
            return "0";
        }
        return parent.getAncestors() + "," + parent.getId();
    }

    private List<DeptVo> buildTree(List<SysDeptDO> depts, Long parentId) {
        List<DeptVo> result = new ArrayList<>();
        for (SysDeptDO dept : depts) {
            if (parentId.equals(dept.getParentId())) {
                result.add(toVo(dept, buildTree(depts, dept.getId())));
            }
        }
        return result;
    }

    private DeptVo toVo(SysDeptDO dept, List<DeptVo> children) {
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

