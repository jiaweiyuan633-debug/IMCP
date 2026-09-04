package cn.admin.scaffold.module.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.admin.scaffold.common.BusinessException;
import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.ResultCode;
import cn.admin.scaffold.module.device.dto.ThingModelQuery;
import cn.admin.scaffold.module.device.dto.ThingModelSaveRequest;
import cn.admin.scaffold.module.device.entity.DeviceDO;
import cn.admin.scaffold.module.device.entity.ThingModelDO;
import cn.admin.scaffold.module.device.mapper.DeviceMapper;
import cn.admin.scaffold.module.device.mapper.ThingModelMapper;
import cn.admin.scaffold.module.device.vo.ThingModelSchemaVo;
import cn.admin.scaffold.module.device.vo.ThingModelVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 物模型服务：分页查询、详情、schema 解析、新增、编辑、删除。
 * 保存时校验 properties/events/services 为合法 JSON，非法抛 PARAM_ERROR。
 * 租户隔离由 TenantLineInnerInterceptor 自动注入，这里不手动操作 TenantContext。
 */
@Service
@RequiredArgsConstructor
public class ThingModelService {

    private static final int ENABLED = 1;

    private final ThingModelMapper thingModelMapper;
    private final ObjectMapper objectMapper;
    private final DeviceMapper deviceMapper;

    public PageResult<ThingModelVo> page(ThingModelQuery query) {
        Page<ThingModelDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ThingModelDO> wrapper = new LambdaQueryWrapper<ThingModelDO>()
                .like(StringUtils.hasText(query.getDeviceType()), ThingModelDO::getDeviceType, query.getDeviceType())
                .like(StringUtils.hasText(query.getName()), ThingModelDO::getName, query.getName())
                .eq(query.getStatus() != null, ThingModelDO::getStatus, query.getStatus())
                .orderByAsc(ThingModelDO::getId);
        IPage<ThingModelDO> result = thingModelMapper.selectPage(page, wrapper);
        List<ThingModelVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public ThingModelVo detail(Long id) {
        return toVo(getRequired(id));
    }

    public ThingModelSchemaVo schema(Long id) {
        ThingModelDO model = getRequired(id);
        return ThingModelSchemaVo.builder()
                .properties(parseJsonArray(model.getPropertiesJson()))
                .events(parseJsonArray(model.getEventsJson()))
                .services(parseJsonArray(model.getServicesJson()))
                .build();
    }

    public Long create(ThingModelSaveRequest request) {
        checkTypeUnique(request.getDeviceType(), null);
        validateJson(request);
        ThingModelDO model = toEntity(request);
        try {
            thingModelMapper.insert(model);
        } catch (DuplicateKeyException exception) {
            // 并发同类型创建：预检通过但唯一键先被他人占用，转精确业务码而非泛化 500
            throw new BusinessException(ResultCode.THING_MODEL_TYPE_EXISTS);
        }
        return model.getId();
    }

    public void update(ThingModelSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "物模型 ID 不能为空");
        }
        getRequired(request.getId());
        checkTypeUnique(request.getDeviceType(), request.getId());
        validateJson(request);
        // 乐观锁：携带 version 时 MP 自动追加 version 条件并递增，冲突时影响行数为 0
        int rows = thingModelMapper.updateById(toEntity(request));
        if (rows == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "物模型已被他人修改，请刷新后重试");
        }
    }

    public void delete(Long id) {
        ThingModelDO model = getRequired(id);
        // 删除引用校验：仍有设备绑定该物模型类型时禁止删除，避免设备展示出现悬空类型
        Long refCount = deviceMapper.selectCount(new LambdaQueryWrapper<DeviceDO>()
                .eq(DeviceDO::getDeviceType, model.getDeviceType()));
        if (refCount != null && refCount > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "存在 " + refCount + " 台设备引用该物模型，请先解绑后再删除");
        }
        thingModelMapper.deleteById(id);
    }

    private ThingModelDO getRequired(Long id) {
        ThingModelDO model = thingModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return model;
    }

    private void checkTypeUnique(String deviceType, Long excludeId) {
        ThingModelDO exists = thingModelMapper.selectOne(new LambdaQueryWrapper<ThingModelDO>()
                .eq(ThingModelDO::getDeviceType, deviceType.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.THING_MODEL_TYPE_EXISTS);
        }
    }

    private void validateJson(ThingModelSaveRequest request) {
        validateJsonField("属性定义", request.getPropertiesJson());
        validateJsonField("事件定义", request.getEventsJson());
        validateJsonField("服务定义", request.getServicesJson());
    }

    private void validateJsonField(String fieldName, String json) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), fieldName + "必须为合法 JSON");
        }
    }

    private List<Map<String, Object>> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JsonProcessingException e) {
            // 已入库数据不应出现非法 JSON；兜底返回空列表，避免查询接口崩溃
            return List.of();
        }
    }

    private ThingModelDO toEntity(ThingModelSaveRequest request) {
        ThingModelDO model = new ThingModelDO();
        model.setId(request.getId());
        model.setDeviceType(request.getDeviceType().trim());
        model.setName(request.getName());
        model.setDescription(request.getDescription());
        model.setPropertiesJson(request.getPropertiesJson());
        model.setEventsJson(request.getEventsJson());
        model.setServicesJson(request.getServicesJson());
        model.setStatus(request.getStatus() == null ? Integer.valueOf(ENABLED) : request.getStatus());
        model.setVersion(request.getVersion());
        return model;
    }

    private ThingModelVo toVo(ThingModelDO model) {
        return ThingModelVo.builder()
                .id(model.getId())
                .deviceType(model.getDeviceType())
                .name(model.getName())
                .description(model.getDescription())
                .propertiesJson(model.getPropertiesJson())
                .eventsJson(model.getEventsJson())
                .servicesJson(model.getServicesJson())
                .status(model.getStatus())
                .version(model.getVersion())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
