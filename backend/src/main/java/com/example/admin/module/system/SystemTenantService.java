package com.example.admin.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.module.system.mapper.SysTenantMapper;
import com.example.admin.module.system.entity.SysUserDO;
import com.example.admin.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemTenantService {

    private static final int DEFAULT_USER_LIMIT = 100;
    private static final long DEFAULT_STORAGE_LIMIT_MB = 1024L;

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;

    public PageResult<SysTenantDO> page(long pageNum, long pageSize, String tenantName) {
        Page<SysTenantDO> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysTenantDO> wrapper = new LambdaQueryWrapper<SysTenantDO>()
                .like(StringUtils.hasText(tenantName), SysTenantDO::getTenantName, tenantName)
                .orderByAsc(SysTenantDO::getId);
        IPage<SysTenantDO> result = tenantMapper.selectPage(page, wrapper);
        return PageResult.of(result, result.getRecords());
    }

    @Transactional
    public Long create(SysTenantDO tenant) {
        tenant.setId(null);
        tenant.setUserLimit(tenant.getUserLimit() == null ? DEFAULT_USER_LIMIT : tenant.getUserLimit());
        tenant.setStorageLimitMb(tenant.getStorageLimitMb() == null
                ? DEFAULT_STORAGE_LIMIT_MB
                : tenant.getStorageLimitMb());
        tenantMapper.insert(tenant);
        validateAdminUser(tenant.getAdminUserId(), tenant.getId());
        return tenant.getId();
    }

    @Transactional
    public void update(SysTenantDO tenant) {
        if (tenant.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户 ID 不能为空");
        }
        SysTenantDO existing = tenantMapper.selectById(tenant.getId());
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
        validateAdminUser(tenant.getAdminUserId() == null ? existing.getAdminUserId() : tenant.getAdminUserId(), existing.getId());
        existing.setTenantName(tenant.getTenantName());
        existing.setTenantCode(tenant.getTenantCode());
        existing.setStatus(tenant.getStatus() == null ? existing.getStatus() : tenant.getStatus());
        existing.setContactName(tenant.getContactName());
        existing.setContactPhone(tenant.getContactPhone());
        existing.setUserLimit(userLimit);
        existing.setStorageLimitMb(storageLimitMb);
        if (Boolean.TRUE.equals(tenant.getClearAdminUserId())) {
            existing.setAdminUserId(null);
        } else if (tenant.getAdminUserId() != null) {
            existing.setAdminUserId(tenant.getAdminUserId());
        }
        tenantMapper.updateById(existing);
    }

    private void validateAdminUser(Long adminUserId, Long tenantId) {
        if (adminUserId == null) {
            return;
        }
        SysUserDO admin = userMapper.selectById(adminUserId);
        if (admin == null || !tenantId.equals(admin.getTenantId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "租户管理员必须是本租户下的用户");
        }
    }

    public void delete(Long id) {
        tenantMapper.deleteById(id);
    }
}

