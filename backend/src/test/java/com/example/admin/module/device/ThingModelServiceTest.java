package com.example.admin.module.device;

import com.example.admin.common.BusinessException;
import com.example.admin.common.ResultCode;
import com.example.admin.module.device.dto.ThingModelSaveRequest;
import com.example.admin.module.device.entity.ThingModelDO;
import com.example.admin.module.device.mapper.DeviceMapper;
import com.example.admin.module.device.mapper.ThingModelMapper;
import com.example.admin.module.device.vo.ThingModelSchemaVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物模型服务单元测试：schema JSON 解析、device_type 唯一性、非法 JSON 校验、
 * 编辑乐观锁冲突、删除设备引用校验。
 */
class ThingModelServiceTest {

    private ThingModelMapper thingModelMapper;
    private DeviceMapper deviceMapper;
    private ThingModelService thingModelService;

    @BeforeEach
    void setUp() {
        thingModelMapper = mock(ThingModelMapper.class);
        deviceMapper = mock(DeviceMapper.class);
        thingModelService = new ThingModelService(thingModelMapper, new ObjectMapper(), deviceMapper);
    }

    @Test
    void schemaParsesJsonArrays() {
        ThingModelDO model = new ThingModelDO();
        model.setId(1L);
        model.setPropertiesJson("[{\"key\":\"temperature\",\"name\":\"温度\",\"dataType\":\"number\",\"unit\":\"℃\",\"mode\":\"ro\"}]");
        model.setEventsJson("[{\"key\":\"alarm\",\"name\":\"告警\",\"params\":[{\"key\":\"level\",\"name\":\"级别\",\"dataType\":\"string\"}]}]");
        model.setServicesJson("[{\"key\":\"reset\",\"name\":\"复位\",\"params\":[]}]");
        when(thingModelMapper.selectById(1L)).thenReturn(model);

        ThingModelSchemaVo schema = thingModelService.schema(1L);

        assertThat(schema.getProperties()).hasSize(1);
        assertThat(schema.getProperties().get(0).get("key")).isEqualTo("temperature");
        assertThat(schema.getEvents()).hasSize(1);
        assertThat(schema.getServices()).hasSize(1);
    }

    @Test
    void schemaReturnsEmptyForMissingJson() {
        ThingModelDO model = new ThingModelDO();
        model.setId(2L);
        when(thingModelMapper.selectById(2L)).thenReturn(model);

        ThingModelSchemaVo schema = thingModelService.schema(2L);

        assertThat(schema.getProperties()).isEmpty();
        assertThat(schema.getEvents()).isEmpty();
        assertThat(schema.getServices()).isEmpty();
    }

    @Test
    void duplicateDeviceTypeRejected() {
        when(thingModelMapper.selectOne(any())).thenReturn(existing(10L, "temp-sensor"));

        assertThatThrownBy(() -> thingModelService.create(request("temp-sensor")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.THING_MODEL_TYPE_EXISTS.getMessage());
    }

    @Test
    void invalidJsonRejected() {
        when(thingModelMapper.selectOne(any())).thenReturn(null);

        ThingModelSaveRequest request = request("temp-sensor");
        request.setPropertiesJson("{not-json");

        assertThatThrownBy(() -> thingModelService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合法 JSON");
    }

    @Test
    void createDuplicateKeyRejectedWithBusinessCode() {
        // 并发同类型创建：预检通过但 insert 命中唯一键 → 转精确业务码而非泛化 500
        when(thingModelMapper.selectOne(any())).thenReturn(null);
        when(thingModelMapper.insert(any(ThingModelDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        assertThatThrownBy(() -> thingModelService.create(request("temp-sensor")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ResultCode.THING_MODEL_TYPE_EXISTS.getMessage());
    }

    @Test
    void updatePassesThroughVersion() {
        when(thingModelMapper.selectById(10L)).thenReturn(existing(10L, "temp-sensor"));
        when(thingModelMapper.selectOne(any())).thenReturn(null);
        when(thingModelMapper.updateById(any(ThingModelDO.class))).thenReturn(1);

        ThingModelSaveRequest request = request("temp-sensor");
        request.setId(10L);
        request.setVersion(3);
        thingModelService.update(request);

        ArgumentCaptor<ThingModelDO> captor = ArgumentCaptor.forClass(ThingModelDO.class);
        verify(thingModelMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
    }

    @Test
    void updateConflictOnStaleVersion() {
        when(thingModelMapper.selectById(10L)).thenReturn(existing(10L, "temp-sensor"));
        when(thingModelMapper.selectOne(any())).thenReturn(null);
        when(thingModelMapper.updateById(any(ThingModelDO.class))).thenReturn(0);

        ThingModelSaveRequest request = request("temp-sensor");
        request.setId(10L);
        request.setVersion(2);

        assertThatThrownBy(() -> thingModelService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新");
    }

    @Test
    void deleteRejectedWhenDevicesReferenceType() {
        when(thingModelMapper.selectById(10L)).thenReturn(existing(10L, "temp-sensor"));
        when(deviceMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> thingModelService.delete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("设备引用");
    }

    @Test
    void deleteAllowedWhenNoDevicesReference() {
        when(thingModelMapper.selectById(10L)).thenReturn(existing(10L, "temp-sensor"));
        when(deviceMapper.selectCount(any())).thenReturn(0L);

        thingModelService.delete(10L);

        verify(thingModelMapper).deleteById(10L);
    }

    private ThingModelSaveRequest request(String deviceType) {
        ThingModelSaveRequest request = new ThingModelSaveRequest();
        request.setDeviceType(deviceType);
        request.setName("温度传感器");
        request.setPropertiesJson("[]");
        return request;
    }

    private ThingModelDO existing(Long id, String deviceType) {
        ThingModelDO model = new ThingModelDO();
        model.setId(id);
        model.setDeviceType(deviceType);
        return model;
    }
}
