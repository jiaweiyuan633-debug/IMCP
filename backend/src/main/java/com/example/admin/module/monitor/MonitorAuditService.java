package com.example.admin.module.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.PageResult;
import com.example.admin.module.system.entity.SysAuditLog;
import com.example.admin.module.system.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MonitorAuditService {

    private final SysAuditLogMapper auditLogMapper;

    public PageResult<SysAuditLog> page(long pageNum, long pageSize, String module, Integer status) {
        Page<SysAuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<SysAuditLog>()
                .like(StringUtils.hasText(module), SysAuditLog::getModule, module)
                .eq(status != null, SysAuditLog::getStatus, status)
                .orderByDesc(SysAuditLog::getId);
        IPage<SysAuditLog> result = auditLogMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }
}
