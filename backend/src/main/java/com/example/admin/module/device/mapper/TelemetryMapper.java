package com.example.admin.module.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.module.device.entity.TelemetryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TelemetryMapper extends BaseMapper<TelemetryDO> {
}
