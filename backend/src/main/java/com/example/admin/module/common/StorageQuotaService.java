package com.example.admin.module.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.module.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final SysFileMapper fileMapper;
    private final SysTenantMapper tenantMapper;

    public void check(long size) {
        Long tenantId = TenantContext.getTenantId();
        SysTenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getStorageLimitMb() == null) {
            return;
        }
        List<SysFileDO> files = fileMapper.selectList(new LambdaQueryWrapper<SysFileDO>()
                .eq(SysFileDO::getTenantId, tenantId));
        long used = files.stream().mapToLong(SysFileDO::getSize).sum();
        long limit = tenant.getStorageLimitMb() * BYTES_PER_MB;
        if (used + size > limit) {
            throw new BusinessException(ResultCode.STORAGE_LIMIT_EXCEEDED);
        }
    }
}
