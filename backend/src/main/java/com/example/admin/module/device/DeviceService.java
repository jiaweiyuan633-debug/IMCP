package com.example.admin.module.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.module.device.dto.DeviceQuery;
import com.example.admin.module.device.dto.DeviceSaveRequest;
import com.example.admin.module.device.entity.DeviceDO;
import com.example.admin.module.device.mapper.DeviceMapper;
import com.example.admin.module.device.vo.DeviceVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 设备管理服务：分页查询、新增、编辑、删除、状态切换。
 * 租户隔离由 TenantLineInnerInterceptor 自动注入，这里不手动操作 TenantContext。
 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int ENABLED = 1;
    private static final int DEFAULT_SORT = 0;

    private final DeviceMapper deviceMapper;

    public PageResult<DeviceVo> page(DeviceQuery query) {
        Page<DeviceDO> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<DeviceDO> wrapper = new LambdaQueryWrapper<DeviceDO>()
                .like(StringUtils.hasText(query.getDeviceCode()), DeviceDO::getDeviceCode, query.getDeviceCode())
                .like(StringUtils.hasText(query.getDeviceName()), DeviceDO::getDeviceName, query.getDeviceName())
                .eq(query.getStatus() != null, DeviceDO::getStatus, query.getStatus())
                .orderByAsc(DeviceDO::getSort)
                .orderByAsc(DeviceDO::getId);
        IPage<DeviceDO> result = deviceMapper.selectPage(page, wrapper);
        List<DeviceVo> records = result.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(result, records);
    }

    public Long create(DeviceSaveRequest request) {
        checkCodeUnique(request.getDeviceCode(), null);
        DeviceDO device = toEntity(request);
        deviceMapper.insert(device);
        return device.getId();
    }

    public void update(DeviceSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "设备 ID 不能为空");
        }
        checkCodeUnique(request.getDeviceCode(), request.getId());
        deviceMapper.updateById(toEntity(request));
    }

    public void updateStatus(Long id, Integer status) {
        DeviceDO device = new DeviceDO();
        device.setId(id);
        device.setStatus(status);
        deviceMapper.updateById(device);
    }

    public void delete(Long id) {
        deviceMapper.deleteById(id);
    }

    private void checkCodeUnique(String deviceCode, Long excludeId) {
        DeviceDO exists = deviceMapper.selectOne(new LambdaQueryWrapper<DeviceDO>()
                .eq(DeviceDO::getDeviceCode, deviceCode.trim()));
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw new BusinessException(ResultCode.DEVICE_CODE_EXISTS);
        }
    }

    private DeviceDO toEntity(DeviceSaveRequest request) {
        DeviceDO device = new DeviceDO();
        device.setId(request.getId());
        device.setDeviceCode(request.getDeviceCode().trim());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setLocation(request.getLocation());
        device.setSort(request.getSort() == null ? DEFAULT_SORT : request.getSort());
        device.setStatus(request.getStatus() == null ? ENABLED : request.getStatus());
        device.setDescription(request.getDescription());
        return device;
    }

    private DeviceVo toVo(DeviceDO device) {
        return DeviceVo.builder()
                .id(device.getId())
                .deviceCode(device.getDeviceCode())
                .deviceName(device.getDeviceName())
                .deviceType(device.getDeviceType())
                .location(device.getLocation())
                .sort(device.getSort())
                .status(device.getStatus())
                .description(device.getDescription())
                .createdAt(device.getCreatedAt())
                .build();
    }
}
