package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.system.entity.SysTenantDO;
import cn.admin.scaffold.module.system.mapper.SysTenantMapper;
import cn.admin.scaffold.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemTenantServiceTest {

    @Mock
    private SysTenantMapper tenantMapper;

    @Mock
    private SysUserMapper userMapper;

    @InjectMocks
    private SystemTenantService tenantService;

    @Test
    void updateKeepsExistingQuotaWhenRequestOmitsQuota() {
        SysTenantDO existing = new SysTenantDO();
        existing.setId(1L);
        existing.setTenantName("旧租户");
        existing.setTenantCode("old");
        existing.setUserLimit(50);
        existing.setStorageLimitMb(512L);
        when(tenantMapper.selectById(1L)).thenReturn(existing);

        SysTenantDO request = new SysTenantDO();
        request.setId(1L);
        request.setTenantName("新租户");
        request.setTenantCode("new");
        tenantService.update(request);

        ArgumentCaptor<SysTenantDO> captor = ArgumentCaptor.forClass(SysTenantDO.class);
        verify(tenantMapper).updateById(captor.capture());
        assertEquals("新租户", captor.getValue().getTenantName());
        assertEquals(50, captor.getValue().getUserLimit());
        assertEquals(512L, captor.getValue().getStorageLimitMb());
    }

    @Test
    void updateRejectsInvalidUserLimit() {
        SysTenantDO existing = new SysTenantDO();
        existing.setId(1L);
        existing.setTenantName("旧租户");
        existing.setTenantCode("old");
        existing.setUserLimit(50);
        existing.setStorageLimitMb(512L);
        when(tenantMapper.selectById(1L)).thenReturn(existing);

        SysTenantDO request = new SysTenantDO();
        request.setId(1L);
        request.setTenantName("新租户");
        request.setTenantCode("new");
        request.setUserLimit(0);
        BusinessException exception = assertThrows(BusinessException.class, () -> tenantService.update(request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(tenantMapper, never()).updateById(any(SysTenantDO.class));
    }
}
