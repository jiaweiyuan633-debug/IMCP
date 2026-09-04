package cn.admin.scaffold.module.common;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.TenantContext;
import cn.admin.scaffold.module.system.entity.SysTenantDO;
import cn.admin.scaffold.module.system.mapper.SysFileMapper;
import cn.admin.scaffold.module.system.mapper.SysTenantMapper;
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

        when(fileMapper.selectObjs(any())).thenReturn(List.of(2L * 1024 * 1024));

        assertThrows(BusinessException.class, () -> storageQuotaService.check(1));
    }
}
