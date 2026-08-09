package com.example.admin.module.common;

import com.example.admin.common.BusinessException;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysFileDO;
import com.example.admin.module.system.entity.SysTenantDO;
import com.example.admin.module.system.mapper.SysFileMapper;
import com.example.admin.module.system.mapper.SysTenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageQuotaServiceTest {

    @Mock
    private SysFileMapper fileMapper;

    @Mock
    private SysTenantMapper tenantMapper;

    @InjectMocks
    private StorageQuotaService storageQuotaService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void throwsWhenTenantQuotaExceeded() {
        TenantContext.setTenantId(1L);
        SysTenantDO tenant = new SysTenantDO();
        tenant.setId(1L);
        tenant.setStorageLimitMb(1L);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        SysFileDO file = new SysFileDO();
        file.setSize(2L * 1024 * 1024);
        when(fileMapper.selectList(any())).thenReturn(List.of(file));

        assertThrows(BusinessException.class, () -> storageQuotaService.check(1));
    }
}
