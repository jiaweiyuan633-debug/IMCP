package com.example.admin.module.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.module.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageQuotaService {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final SysFileMapper fileMapper;
    private final SysTenantMapper tenantMapper;

    public void check(long size) {
        StorageQuotaVo quota = usage();
        if (quota.getLimitBytes() == null || quota.getUsedBytes() + size <= quota.getLimitBytes()) {
            return;
        }
        throw new BusinessException(ResultCode.STORAGE_LIMIT_EXCEEDED);
    }

    public StorageQuotaVo usage() {
        Long tenantId = TenantContext.getTenantId();
        SysTenantDO tenant = tenantId == null ? null : tenantMapper.selectById(tenantId);
        long used = usedBytes(tenantId);
        if (tenant == null || tenant.getStorageLimitMb() == null) {
            return StorageQuotaVo.builder()
                    .usedBytes(used)
                    .limitBytes(null)
                    .percent(null)
                    .unlimited(true)
                    .build();
        }
        long limit = tenant.getStorageLimitMb() * BYTES_PER_MB;
        int percent = limit == 0 ? 0 : (int) Math.min(100, used * 100 / limit);
        return StorageQuotaVo.builder()
                .usedBytes(used)
                .limitBytes(limit)
                .percent(percent)
                .unlimited(false)
                .build();
    }

    private long usedBytes(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        Object value = fileMapper.selectObjs(new QueryWrapper<SysFileDO>()
                .select("COALESCE(SUM(size), 0)")
                .eq("tenant_id", tenantId)).stream().findFirst().orElse(null);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
