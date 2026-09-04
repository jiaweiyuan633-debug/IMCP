package cn.admin.scaffold.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.annotation.DataScope;
import cn.admin.scaffold.module.system.entity.SysFieldAuditLogDO;
import cn.admin.scaffold.module.system.mapper.SysFieldAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MonitorFieldAuditService {

    private final SysFieldAuditLogMapper fieldAuditLogMapper;

    @DataScope(tables = {"sys_field_audit_log"})
    public PageResult<SysFieldAuditLogDO> page(long pageNum, long pageSize, String module,
                                               String entityName, String action) {
        Page<SysFieldAuditLogDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysFieldAuditLogDO> wrapper = new LambdaQueryWrapper<SysFieldAuditLogDO>()
                .like(StringUtils.hasText(module), SysFieldAuditLogDO::getModule, module)
                .like(StringUtils.hasText(entityName), SysFieldAuditLogDO::getEntityName, entityName)
                .eq(StringUtils.hasText(action), SysFieldAuditLogDO::getAction, action)
                .orderByDesc(SysFieldAuditLogDO::getId);
        IPage<SysFieldAuditLogDO> result = fieldAuditLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }
}
