package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysTenant;
import com.example.admin.module.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SystemTenantService {

    private final SysTenantMapper tenantMapper;

    public PageResult<SysTenant> page(long pageNum, long pageSize, String tenantName) {
        Page<SysTenant> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<SysTenant>()
                .like(StringUtils.hasText(tenantName), SysTenant::getTenantName, tenantName)
                .orderByAsc(SysTenant::getId);
        IPage<SysTenant> result = tenantMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    public Long create(SysTenant tenant) {
        tenant.setId(null);
        tenant.setUserLimit(tenant.getUserLimit() == null ? 100 : tenant.getUserLimit());
        tenant.setStorageLimitMb(tenant.getStorageLimitMb() == null ? 1024 : tenant.getStorageLimitMb());
        tenantMapper.insert(tenant);
        return tenant.getId();
    }

    public void update(SysTenant tenant) {
        if (tenant.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户 ID 不能为空");
        }
        SysTenant existing = tenantMapper.selectById(tenant.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (!StringUtils.hasText(tenant.getTenantName()) || !StringUtils.hasText(tenant.getTenantCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户名称和编码不能为空");
        }
        Integer userLimit = tenant.getUserLimit() == null ? existing.getUserLimit() : tenant.getUserLimit();
        Long storageLimitMb = tenant.getStorageLimitMb() == null ? existing.getStorageLimitMb() : tenant.getStorageLimitMb();
        if (userLimit == null || userLimit < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户上限必须大于 0");
        }
        if (storageLimitMb == null || storageLimitMb < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "存储上限不能小于 0");
        }
        existing.setTenantName(tenant.getTenantName());
        existing.setTenantCode(tenant.getTenantCode());
        existing.setStatus(tenant.getStatus() == null ? existing.getStatus() : tenant.getStatus());
        existing.setContactName(tenant.getContactName());
        existing.setContactPhone(tenant.getContactPhone());
        existing.setUserLimit(userLimit);
        existing.setStorageLimitMb(storageLimitMb);
        if (tenant.getAdminUserId() != null) {
            existing.setAdminUserId(tenant.getAdminUserId());
        }
        tenantMapper.updateById(existing);
    }

    public void delete(Long id) {
        tenantMapper.deleteById(id);
    }
}

