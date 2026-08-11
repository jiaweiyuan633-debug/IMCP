package com.example.admin.module.device;

import com.example.admin.AbstractIntegrationTest;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.device.dto.DeviceQuery;
import com.example.admin.module.device.dto.DeviceSaveRequest;
import com.example.admin.module.device.vo.DeviceVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 设备服务集成测试：验证 MyBatis-Plus 租户拦截器 + 唯一编码校验在真实 MySQL 上生效。
 */
class DeviceServiceIT extends AbstractIntegrationTest {

    @Autowired
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createPersistsAndListable() {
        DeviceSaveRequest request = request("IT-DEV-001", "集成测试设备");

        Long id = deviceService.create(request);

        assertThat(id).isNotNull();
        DeviceQuery query = new DeviceQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setDeviceCode("IT-DEV-001");
        PageResult<DeviceVo> result = deviceService.page(query);
        assertThat(result.getRecords()).extracting(DeviceVo::getDeviceCode).contains("IT-DEV-001");
    }

    @Test
    void duplicateCodeRejectedWithinTenant() {
        deviceService.create(request("IT-DEV-002", "设备A"));

        assertThatThrownBy(() -> deviceService.create(request("IT-DEV-002", "设备B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.DEVICE_CODE_EXISTS.getMessage());
    }

    @Test
    void tenantIsolationKeepsRecordsApart() {
        TenantContext.setTenantId(1L);
        deviceService.create(request("IT-DEV-100", "租户1设备"));

        TenantContext.setTenantId(2L);
        // 另一租户可用同编码（唯一性按租户隔离）
        deviceService.create(request("IT-DEV-100", "租户2设备"));

        TenantContext.setTenantId(2L);
        DeviceQuery query = new DeviceQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        // 租户2 只看到自己的记录
        assertThat(deviceService.page(query).getRecords())
                .extracting(DeviceVo::getDeviceName)
                .containsExactly("租户2设备");
    }

    private DeviceSaveRequest request(String code, String name) {
        DeviceSaveRequest request = new DeviceSaveRequest();
        request.setDeviceCode(code);
        request.setDeviceName(name);
        request.setDeviceType("IT");
        request.setStatus(1);
        return request;
    }
}
