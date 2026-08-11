package com.example.admin.module.device;

import com.example.admin.AbstractIntegrationTest;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.device.dto.ThingModelQuery;
import com.example.admin.module.device.dto.ThingModelSaveRequest;
import com.example.admin.module.device.vo.ThingModelSchemaVo;
import com.example.admin.module.device.vo.ThingModelVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 物模型服务集成测试：CRUD 往返 + schema 解析 + 租户隔离在真实 MySQL 上生效。
 */
class ThingModelServiceIT extends AbstractIntegrationTest {

    @Autowired
    private ThingModelService thingModelService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDetailAndSchemaRoundTrip() {
        ThingModelSaveRequest request = request("IT-TM-001", "温度传感器");
        request.setPropertiesJson("[{\"key\":\"temperature\",\"name\":\"温度\",\"dataType\":\"number\",\"unit\":\"℃\",\"mode\":\"ro\"}]");
        request.setEventsJson("[{\"key\":\"alarm\",\"name\":\"告警\",\"params\":[{\"key\":\"level\",\"name\":\"级别\",\"dataType\":\"string\"}]}]");
        request.setServicesJson("[{\"key\":\"reset\",\"name\":\"复位\",\"params\":[]}]");

        Long id = thingModelService.create(request);

        assertThat(id).isNotNull();
        ThingModelVo detail = thingModelService.detail(id);
        assertThat(detail.getDeviceType()).isEqualTo("IT-TM-001");
        ThingModelSchemaVo schema = thingModelService.schema(id);
        assertThat(schema.getProperties()).hasSize(1);
        assertThat(schema.getEvents()).hasSize(1);
        assertThat(schema.getServices()).hasSize(1);
    }

    @Test
    void duplicateTypeRejectedWithinTenant() {
        thingModelService.create(request("IT-TM-002", "设备A"));

        assertThatThrownBy(() -> thingModelService.create(request("IT-TM-002", "设备B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.THING_MODEL_TYPE_EXISTS.getMessage());
    }

    @Test
    void tenantIsolationKeepsRecordsApart() {
        TenantContext.setTenantId(1L);
        thingModelService.create(request("IT-TM-100", "租户1物模型"));

        TenantContext.setTenantId(2L);
        // 另一租户可用同 device_type（唯一键按租户隔离）
        thingModelService.create(request("IT-TM-100", "租户2物模型"));

        TenantContext.setTenantId(2L);
        ThingModelQuery query = new ThingModelQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setDeviceType("IT-TM-100");
        assertThat(thingModelService.page(query).getRecords())
                .extracting(ThingModelVo::getName)
                .containsExactly("租户2物模型");
    }

    private ThingModelSaveRequest request(String deviceType, String name) {
        ThingModelSaveRequest request = new ThingModelSaveRequest();
        request.setDeviceType(deviceType);
        request.setName(name);
        request.setStatus(1);
        return request;
    }
}
