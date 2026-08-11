package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.dto.DataPermissionSaveRequest;
import com.example.admin.module.system.entity.SysDataPermissionDO;
import com.example.admin.module.system.mapper.SysDataPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据权限表-列映射配置（批次2b）：CRUD 后即时重载拦截器缓存，新增受控表无需发版。
 */
@Service
@RequiredArgsConstructor
public class SystemDataPermissionService {

    private final SysDataPermissionMapper mapper;
    private final DataPermissionRuleResolver ruleResolver;

    public List<SysDataPermissionDO> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysDataPermissionDO>()
                .orderByAsc(SysDataPermissionDO::getId));
    }

    @Transactional
    public Long create(DataPermissionSaveRequest request) {
        validateColumns(request);
        SysDataPermissionDO row = new SysDataPermissionDO();
        apply(row, request);
        mapper.insert(row);
        ruleResolver.reload();
        return row.getId();
    }

    @Transactional
    public void update(DataPermissionSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "id 不能为空");
        }
        validateColumns(request);
        SysDataPermissionDO row = mapper.selectById(request.getId());
        if (row == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND.getCode(), "数据权限配置不存在");
        }
        apply(row, request);
        mapper.updateById(row);
        ruleResolver.reload();
    }

    @Transactional
    public void delete(Long id) {
        mapper.deleteById(id);
        ruleResolver.reload();
    }

    public void reload() {
        ruleResolver.reload();
    }

    /** user_column 与 username_column 至少配置其一，否则过滤无意义。 */
    private void validateColumns(DataPermissionSaveRequest request) {
        boolean hasUserColumn = request.getUserColumn() != null && !request.getUserColumn().isBlank();
        boolean hasUsernameColumn = request.getUsernameColumn() != null && !request.getUsernameColumn().isBlank();
        if (!hasUserColumn && !hasUsernameColumn) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户ID列名与用户名列名至少配置一个");
        }
    }

    private void apply(SysDataPermissionDO row, DataPermissionSaveRequest request) {
        row.setTableName(request.getTableName().toLowerCase().trim());
        row.setUserColumn(request.getUserColumn());
        row.setUsernameColumn(request.getUsernameColumn());
        row.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        row.setRemark(request.getRemark());
    }
}
